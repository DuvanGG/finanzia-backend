package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador tipado de una sesión (fila del ledger de refresh tokens).
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId from(UUID value) {
        return new SessionId(value);
    }

    public static SessionId from(String value) {
        try {
            return new SessionId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Invalid SessionId: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}