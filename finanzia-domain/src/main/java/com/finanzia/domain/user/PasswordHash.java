package com.finanzia.domain.user;

import com.finanzia.domain.DomainException;

import java.util.Objects;

/**
 * Hash irreversible de una contraseña. El dominio no puede recuperar el texto
 * en claro a partir de este valor.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("Password hash must not be blank");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    public static PasswordHash from(CharSequence value) {
        return new PasswordHash(value.toString());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PasswordHash h && value.equals(h.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}