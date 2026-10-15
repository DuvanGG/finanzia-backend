package com.finanzia.domain.user;

import com.finanzia.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    @Test
    void rejectsShortPasswords() {
        assertThrows(DomainException.class, () -> new Password("abcdefghijk"));
    }

    @Test
    void rejectsRepeatedCharacterPasswords() {
        assertThrows(DomainException.class, () -> new Password("aaaaaaaaaaaa"));
        assertThrows(DomainException.class, () -> new Password("11111111111111"));
    }

    @Test
    void acceptsCompliantPasswords() {
        new Password("correcthorsebatterystaple");
        new Password("P@ssw0rd-2026-verylong");
    }

    @Test
    void rejectsBlankPasswords() {
        assertThrows(DomainException.class, () -> new Password(null));
        assertThrows(DomainException.class, () -> new Password(""));
    }
}