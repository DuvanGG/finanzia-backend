package com.finanzia.application.exception;

import java.time.Duration;

/**
 * La cuenta está temporalmente bloqueada por intentos fallidos repetidos.
 */
public class AccountLockedException extends AuthException {

    private final Duration remaining;

    public AccountLockedException(Duration remaining) {
        super("Account is locked");
        this.remaining = remaining;
    }

    public Duration remaining() {
        return remaining;
    }
}