package com.finanzia.domain.event;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class RefreshTokenReused extends DomainEvent {

    private final SessionId sessionId;
    private final TokenFamilyId familyId;

    public RefreshTokenReused(UserId userId, SessionId sessionId, TokenFamilyId familyId, Instant occurredAt) {
        super(occurredAt, userId);
        this.sessionId = sessionId;
        this.familyId = familyId;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public TokenFamilyId familyId() {
        return familyId;
    }
}