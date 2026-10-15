package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

/**
 * Correo electrónico normalizado: trim, minúsculas y dominios IDN → punycode.
 */
public final class Email {

    private static final String LOCAL_PART = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*";
    private static final String DOMAIN_PART = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*";
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^" + LOCAL_PART + "@" + DOMAIN_PART + "$");

    private final String value;

    public Email(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("Email must not be blank");
        }
        String trimmed = raw.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            throw new DomainException("Invalid email: " + trimmed);
        }
        String local = trimmed.substring(0, at).toLowerCase(Locale.ROOT);
        String domainRaw = trimmed.substring(at + 1);
        String domain;
        try {
            domain = IDN.toASCII(domainRaw).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid email domain: " + domainRaw, e);
        }
        Objects.requireNonNull(local, "local must not be null");
        String normalized = local + "@" + domain;
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("Invalid email: " + normalized);
        }
        this.value = normalized;
    }

    public static Email of(String raw) {
        return new Email(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Email other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}