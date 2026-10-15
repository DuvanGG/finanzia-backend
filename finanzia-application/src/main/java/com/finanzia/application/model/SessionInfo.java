package com.finanzia.application.model;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.session.Session;

import java.time.Instant;

/**
 * Vista de una sesión para listar/revocar desde el API.
 */
public record SessionInfo(SessionId id,
                          UserId userId,
                          String device,
                          IpAddress ip,
                          Instant createdAt,
                          Instant expiresAt,
                          Instant hadLastUsed
) {

    public static SessionInfo from(Session session) {
        Instant lastUsed = session.consumedAt() != null ? session.consumedAt() : session.createdAt();
        return new SessionInfo(session.id(), session.userId(), session.device(), session.ip(),
                session.createdAt(), session.expiresAt(), lastUsed);
    }
}