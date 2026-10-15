package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador tipado de un usuario.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(UUID value) {
        return new UserId(value);
    }

    public static UserId from(String value) {
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Invalid UserId: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}