package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.time.Duration;
import java.util.Objects;

/**
 * Tiempo de vida de un token o código.
 */
public record TokenTtl(Duration value) {

    public TokenTtl {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new DomainException("Token TTL must be positive");
        }
    }

    public static TokenTtl of(Duration value) {
        return new TokenTtl(value);
    }
}