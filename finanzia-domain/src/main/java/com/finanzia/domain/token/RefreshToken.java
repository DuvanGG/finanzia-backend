package com.finanzia.domain.token;

import com.finanzia.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Refresh token opaco de alta entropía. El valor en claro solo viaja al cliente;
 * en el ledger se persiste únicamente su SHA-256.
 */
public record RefreshToken(String value, Instant expiresAt) {

    public RefreshToken {
        if (value == null || value.isBlank()) {
            throw new DomainException("Refresh token must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static RefreshToken of(String value, Instant expiresAt) {
        return new RefreshToken(value, expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }
}