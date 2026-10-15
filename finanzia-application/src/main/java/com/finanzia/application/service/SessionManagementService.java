package com.finanzia.application.service;

import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.util.Tokens;
import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.model.SessionInfo;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gestión de sesiones del propio usuario: listado, logout, logout-all y
 * revocación de un dispositivo concreto (con chequeo de propiedad).
 */
public class SessionManagementService {

    private final RefreshTokenStore sessions;
    private final AuditLog audit;
    private final Clock clock;

    public SessionManagementService(RefreshTokenStore sessions, AuditLog audit, Clock clock) {
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    public List<SessionInfo> list(UserId userId, IpAddress ip, String device, String correlationId) {
        Instant now = clock.now();
        sessions.purgeForUser(userId, now);
        return sessions.listActiveByUser(userId, now).stream().map(SessionInfo::from).toList();
    }

    public void revokeSession(UserId ownerId, SessionId sessionId, IpAddress ip,
                              String device, String correlationId) {
        Session session = sessions.findById(sessionId).orElseThrow(NotFoundException::new);
        if (!session.userId().equals(ownerId)) {
            throw new NotFoundException();
        }
        Instant now = clock.now();
        sessions.revoke(session.id(), now);
        audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS, ownerId,
                ip, device, correlationId, Map.of("reason", RevocationReason.DEVICE_REVOKE.name(),
                        "sessionId", session.id().toString()),
                "Sesión revocada por el usuario"));
    }

    public void logout(String refreshTokenValue, IpAddress ip, String device, String correlationId) {
        Instant now = clock.now();
        Optional<Session> session = sessions.findByTokenHash(Tokens.sha256Hex(refreshTokenValue));
        if (session.isPresent() && session.get().isActiveAt(now)) {
            sessions.revoke(session.get().id(), now);
            audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS,
                    session.get().userId(), ip, device, correlationId,
                    Map.of("reason", RevocationReason.LOGOUT.name(), "sessionId", session.get().id().toString()),
                    "Logout de una sesión"));
        }
    }

    public void logoutAll(UserId userId, IpAddress ip, String device, String correlationId) {
        Instant now = clock.now();
        sessions.revokeAllByUser(userId, now);
        audit.record(new AuditRecord(now, AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS, userId,
                ip, device, correlationId, Map.of("reason", RevocationReason.LOGOUT_ALL.name()),
                "Logout de todas las sesiones"));
    }
}