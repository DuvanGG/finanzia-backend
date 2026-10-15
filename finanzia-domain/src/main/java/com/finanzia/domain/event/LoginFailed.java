package com.finanzia.domain.event;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;

import java.time.Instant;

/**
 * Intento de login fallido. userId puede ser nulo cuando el email no existe.
 */
public final class LoginFailed extends DomainEvent {

    private final IpAddress ip;

    public LoginFailed(UserId userId, IpAddress ip, Instant occurredAt) {
        super(occurredAt, userId);
        this.ip = ip;
    }

    public IpAddress ip() {
        return ip;
    }
}