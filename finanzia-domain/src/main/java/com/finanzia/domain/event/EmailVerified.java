package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class EmailVerified extends DomainEvent {

    public EmailVerified(UserId userId, Instant occurredAt) {
        super(occurredAt, userId);
    }
}