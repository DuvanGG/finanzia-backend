package com.finanzia.domain.token;

import com.finanzia.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Access token JWS emitido al cliente. Contiene el userId y el snapshot de roles.
 */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new DomainException("Access token must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static AccessToken of(String value, Instant expiresAt) {
        return new AccessToken(value, expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }
}