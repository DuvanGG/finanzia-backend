package com.finanzia.domain.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de la familia de rotación de refresh tokens.
 * Todas las sesiones que compartan family_id se revocan juntas ante un reuso.
 */
public record TokenFamilyId(UUID value) {

    public TokenFamilyId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TokenFamilyId random() {
        return new TokenFamilyId(UUID.randomUUID());
    }

    public static TokenFamilyId from(UUID value) {
        return new TokenFamilyId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}