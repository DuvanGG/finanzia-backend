package com.finanzia.application.service;

import com.finanzia.application.exception.InvalidCredentialsException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.event.CredentialChangeReason;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.user.Password;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.User;

import java.time.Instant;
import java.util.Map;

/**
 * Cambio de contraseña por el propio usuario (requiere la contraseña actual).
 * Revoca todas las demás sesiones manteniendo la actual.
 */
public class ChangePasswordService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final RefreshTokenStore sessions;
    private final AuditLog audit;
    private final Clock clock;

    public ChangePasswordService(UserRepository users, PasswordHasher hasher, RefreshTokenStore sessions,
                                 AuditLog audit, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    public void changePassword(UserId userId, String currentPassword, String newPassword,
                               SessionId currentSessionId, IpAddress ip, String device,
                               String correlationId) {
        new Password(newPassword);
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (user.isDisabled()) {
            throw new InvalidCredentialsException();
        }
        if (!hasher.matches(currentPassword, user.passwordHash().value())) {
            throw new InvalidCredentialsException();
        }
        Instant now = clock.now();
        user.changePassword(PasswordHash.of(hasher.hash(newPassword)), now);
        users.save(user);

        sessions.revokeSessionsExcept(userId, currentSessionId, now);

        audit.record(new AuditRecord(now, AuditEventType.CREDENTIALS_CHANGED, AuditOutcome.SUCCESS, userId,
                ip, device, correlationId,
                Map.of("reason", CredentialChangeReason.PASSWORD_CHANGE.name()), "Contraseña cambiada"));
        audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS, userId,
                ip, device, correlationId,
                Map.of("reason", RevocationReason.PASSWORD_CHANGE.name(), "scope", "all_except_current"),
                "Sesiones revocadas por cambio de contraseña"));
    }
}