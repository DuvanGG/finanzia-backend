package com.finanzia.domain.event;

import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class UserRegistered extends DomainEvent {

    private final Email email;

    public UserRegistered(UserId userId, Email email, Instant occurredAt) {
        super(occurredAt, userId);
        this.email = email;
    }

    public Email email() {
        return email;
    }
}