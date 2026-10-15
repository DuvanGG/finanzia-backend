package com.finanzia.domain.user;

import com.finanzia.domain.DomainException;

import java.util.Objects;

/**
 * Contraseña en claro durante la validación de política.
 * <p>
 * La política exige longitud mínima de 12 caracteres y rechaza contraseñas que
 * consistan solo de caracteres repetidos. El hashing ocurre fuera del dominio
 * (puerto {@code PasswordHasher}).
 */
public record Password(String value) {

    public static final int MIN_LENGTH = 12;

    public Password {
        if (value == null || value.isBlank()) {
            throw new DomainException("Password must not be blank");
        }
        if (value.length() < MIN_LENGTH) {
            throw new DomainException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (value.chars().distinct().count() == 1) {
            throw new DomainException("Password must not consist of repeated characters");
        }
    }

    public static Password of(String value) {
        return new Password(value);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Password p && value.equals(p.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}