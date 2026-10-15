package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Duration;
import java.time.Instant;

public final class AccountLocked extends DomainEvent {

    private final Duration lockDuration;

    public AccountLocked(UserId userId, Duration lockDuration, Instant occurredAt) {
        super(occurredAt, userId);
        this.lockDuration = lockDuration;
    }

    public Duration lockDuration() {
        return lockDuration;
    }
}