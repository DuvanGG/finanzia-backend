package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.config.AuthSettings.LockoutThreshold;
import com.finanzia.application.exception.AccountLockedException;
import com.finanzia.application.exception.EmailVerificationRequiredException;
import com.finanzia.application.exception.InvalidCredentialsException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.model.LoginResult;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.TokenProvider;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.DomainException;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.session.Session;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Login con email + contraseña. Verifica credenciales, aplica lockout
 * progresivo por cuenta/IP (estado en Redis) y emite {@code TokenPair}.
 * Respuesta idéntica (401) para email desconocido y contraseña errónea; el
 * lockout solo se registra sobre cuentas existentes.
 */
public class LoginService {

    public static final String DUMMY_HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenProvider tokenProvider;
    private final RefreshTokenStore sessions;
    private final LoginAttemptStore attempts;
    private final AuditLog audit;
    private final AuthSettings settings;
    private final Clock clock;

    public LoginService(UserRepository users, PasswordHasher hasher, TokenProvider tokenProvider,
                        RefreshTokenStore sessions, LoginAttemptStore attempts, AuditLog audit,
                        AuthSettings settings, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.tokenProvider = tokenProvider;
        this.sessions = sessions;
        this.attempts = attempts;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    public LoginResult login(String emailRaw, String passwordRaw, IpAddress ip,
                             String device, String correlationId) {
        Instant now = clock.now();
        Email email;
        try {
            email = Email.of(emailRaw);
        } catch (DomainException e) {
            email = null;
        }
        User user = email == null ? null : users.findByEmail(email).orElse(null);

        if (user != null && user.isActive() && attempts.isAccountLocked(user.id())) {
            Duration remaining = attempts.accountLockRemaining(user.id()).orElse(Duration.ZERO);
            throw new AccountLockedException(remaining);
        }
        if (attempts.isIpBlocked(ip)) {
            throw new RateLimitedException();
        }

        String candidateHash = user != null ? user.passwordHash().value() : DUMMY_HASH;
        boolean passwordOk = hasher.matches(passwordRaw, candidateHash);

        if (!passwordOk || user == null) {
            User target = passwordOk ? null : user;
            registerFailure(target, ip, now, device, correlationId);
            throw new InvalidCredentialsException();
        }
        if (user.isDisabled()) {
            audit.record(new AuditRecord(now, AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE, user.id(),
                    ip, device, correlationId, Map.of("reason", "account_disabled"), "Login rechazado"));
            throw new InvalidCredentialsException();
        }
        if (!user.hasVerifiedEmail()) {
            audit.record(new AuditRecord(now, AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE, user.id(),
                    ip, device, correlationId, Map.of("reason", "email_not_verified"), "Login rechazado"));
            throw new EmailVerificationRequiredException();
        }

        attempts.clear(user.id(), ip);
        audit.record(new AuditRecord(now, AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of(), "Login correcto"));

        enforceSessionLimit(user, now, ip, device, correlationId);

        RefreshToken refresh = tokenProvider.issueRefreshToken(now, settings.refreshTokenTtl());
        Session session = Session.create(SessionId.random(), user.id(), TokenFamilyId.random(),
                Tokens.sha256Hex(refresh.value()), device, ip, now, settings.refreshTokenTtl());
        sessions.save(session);

        AccessToken access = tokenProvider.issueAccessToken(user.id(), session.id(), Set.of(user.role()),
                now, settings.accessTokenTtl(), settings.issuer());

        return new LoginResult(access, refresh, session.id(), user.id(), Set.of(user.role()));
    }

    private void enforceSessionLimit(User user, Instant now, IpAddress ip, String device, String correlationId) {
        int active = sessions.countActiveByUser(user.id(), now);
        if (active >= settings.maxActiveSessions()) {
            sessions.findOldestActiveByUser(user.id(), now).ifPresent(oldest -> {
                sessions.revoke(oldest.id(), now);
                audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS,
                        user.id(), ip, device, correlationId,
                        Map.of("reason", RevocationReason.MAX_SESSIONS.name(),
                                "sessionId", oldest.id().toString()),
                        "Sesión más antigua revocada por límite de sesiones activas"));
            });
        }
    }

    private void registerFailure(User user, IpAddress ip, Instant now, String device, String correlationId) {
        if (user != null && user.isActive()) {
            attempts.incrementFailures(user.id(), ip, settings.loginFailureWindow());
            long failures = attempts.failureCount(user.id());
            Optional<LockoutThreshold> threshold = settings.accountLockout().stream()
                    .filter(t -> failures >= t.failures())
                    .findFirst();
            threshold.ifPresent(t -> {
                attempts.lockAccount(user.id(), t.lockFor());
                audit.record(new AuditRecord(now, AuditEventType.ACCOUNT_LOCKED, AuditOutcome.FAILURE,
                        user.id(), ip, device, correlationId,
                        Map.of("failures", Long.toString(failures), "lockFor", t.lockFor().toString()),
                        "Cuenta bloqueada por intentos fallidos"));
            });
            audit.record(new AuditRecord(now, AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE, user.id(),
                    ip, device, correlationId, Map.of("failures", Long.toString(failures)),
                    "Intento de login fallido"));
        } else {
            attempts.incrementIpFailures(ip, settings.loginFailureWindow());
            long ipFailures = attempts.ipFailureCount(ip);
            Optional<LockoutThreshold> threshold = settings.ipLockout().stream()
                    .filter(t -> ipFailures >= t.failures())
                    .findFirst();
            threshold.ifPresent(t -> attempts.blockIp(ip, t.lockFor()));
            audit.record(new AuditRecord(now, AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE, null,
                    ip, device, correlationId,
                    Map.of("ipFailures", Long.toString(ipFailures)), "Intento de login fallido (email desconocido)"));
        }
    }
}