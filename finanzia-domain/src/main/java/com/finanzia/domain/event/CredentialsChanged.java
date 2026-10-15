package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Instant;

public final class CredentialsChanged extends DomainEvent {

    private final CredentialChangeReason reason;

    public CredentialsChanged(UserId userId, CredentialChangeReason reason, Instant occurredAt) {
        super(occurredAt, userId);
        this.reason = reason;
    }

    public CredentialChangeReason reason() {
        return reason;
    }
}