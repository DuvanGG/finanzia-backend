package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador único de un access token (claim jti).
 */
public record JwtId(UUID value) {

    public JwtId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static JwtId random() {
        return new JwtId(UUID.randomUUID());
    }

    public static JwtId from(UUID value) {
        return new JwtId(value);
    }

    public static JwtId from(String value) {
        try {
            return new JwtId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Invalid JwtId: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}