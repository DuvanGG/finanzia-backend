package com.finanzia.domain.event;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class SessionRevoked extends DomainEvent {

    private final SessionId sessionId;
    private final RevocationReason reason;

    public SessionRevoked(UserId userId, SessionId sessionId, RevocationReason reason, Instant occurredAt) {
        super(occurredAt, userId);
        this.sessionId = sessionId;
        this.reason = reason;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public RevocationReason reason() {
        return reason;
    }
}