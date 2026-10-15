package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidCodeException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.application.port.out.EmailSender;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.util.Codes;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.event.CredentialChangeReason;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;
import com.finanzia.domain.user.Password;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Recuperación de contraseña olvidada: petición de código de un solo uso y
 * reseteo con invalidación del código y revocación de todas las sesiones.
 * La petición responde idéntico para emails no registrados (sin revelar).
 */
public class ResetPasswordService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final EmailCodeStore codes;
    private final EmailSender emailSender;
    private final LoginAttemptStore attempts;
    private final RefreshTokenStore sessions;
    private final AuditLog audit;
    private final AuthSettings settings;
    private final Clock clock;

    public ResetPasswordService(UserRepository users, PasswordHasher hasher, EmailCodeStore codes,
                                EmailSender emailSender, LoginAttemptStore attempts, RefreshTokenStore sessions,
                                AuditLog audit, AuthSettings settings, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.codes = codes;
        this.emailSender = emailSender;
        this.attempts = attempts;
        this.sessions = sessions;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    public void requestReset(String emailRaw, String device, IpAddress ip, String correlationId) {
        if (!attempts.isWithinLimit(ip, "PASSWORD_RESET", settings.recoveryLimit(),
                Duration.ofSeconds(settings.recoveryWindowSeconds()))) {
            throw new RateLimitedException();
        }
        Email email;
        try {
            email = Email.of(emailRaw);
        } catch (RuntimeException e) {
            return;
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null || !user.hasVerifiedEmail() || user.isDisabled()) {
            return;
        }
        Instant now = clock.now();
        codes.invalidateByUserAndPurpose(user.id(), CodePurpose.RESET, now);
        String code = Codes.generate(8);
        EmailCode emailCode = EmailCode.create(user.id(), CodePurpose.RESET, Tokens.sha256Hex(code), now,
                settings.resetCodeTtl());
        codes.save(emailCode);
        emailSender.send(user.email(), "Recupera tu contraseña en FinanzIA",
                "Tu código de recuperación es: " + code);
    }

    public void resetPassword(String code, String newPassword, IpAddress ip, String device,
                              String correlationId) {
        new Password(newPassword);
        Instant now = clock.now();
        EmailCode emailCode = codes.findByCodeHashAndPurpose(Tokens.sha256Hex(code), CodePurpose.RESET)
                .orElseThrow(InvalidCodeException::new);
        if (emailCode.isConsumed() || emailCode.isExpiredAt(now)) {
            throw new InvalidCodeException();
        }
        User user = users.findById(emailCode.userId()).orElseThrow(InvalidCodeException::new);
        if (user.isDisabled()) {
            throw new InvalidCodeException();
        }
        user.changePassword(PasswordHash.of(hasher.hash(newPassword)), now);
        users.save(user);
        codes.markConsumed(emailCode.id(), now);
        sessions.revokeAllByUser(user.id(), now);

        audit.record(new AuditRecord(now, AuditEventType.CREDENTIALS_CHANGED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of("reason", CredentialChangeReason.PASSWORD_RESET.name()),
                "Contraseña restablecida"));
        audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of("reason", RevocationReason.PASSWORD_RESET.name()),
                "Sesiones revocadas por reseteo de contraseña"));
    }
}