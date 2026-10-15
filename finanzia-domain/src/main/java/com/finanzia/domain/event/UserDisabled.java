package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class UserDisabled extends DomainEvent {

    public UserDisabled(UserId userId, Instant occurredAt) {
        super(occurredAt, userId);
    }
}