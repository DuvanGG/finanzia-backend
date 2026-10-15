package com.finanzia.application.service;

import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.user.User;

import java.time.Instant;
import java.util.Map;

/**
 * Deshabilitación de una cuenta (rol ADMIN). Revoca todas sus sesiones y evita
 * futuros logins. No elimina datos.
 */
public class DisableAccountService {

    private final UserRepository users;
    private final RefreshTokenStore sessions;
    private final AuditLog audit;
    private final Clock clock;

    public DisableAccountService(UserRepository users, RefreshTokenStore sessions, AuditLog audit, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    public void disable(UserId userId, IpAddress ip, String device, String correlationId) {
        User user = users.findById(userId).orElseThrow(NotFoundException::new);
        Instant now = clock.now();
        user.disable(now);
        users.save(user);
        sessions.revokeAllByUser(user.id(), now);

        audit.record(new AuditRecord(now, AuditEventType.USER_DISABLED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of(), "Cuenta deshabilitada"));
        audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of("reason", RevocationReason.ACCOUNT_DISABLED.name()),
                "Sesiones revocadas por deshabilitación de cuenta"));
    }
}