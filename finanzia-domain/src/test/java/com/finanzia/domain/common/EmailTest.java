package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void normalizesCaseWhitespaceAndDuplicates() {
        Email a = Email.of("  USER@Example.COM  ");
        Email b = Email.of("user@example.com");
        assertEquals(a, b);
        assertEquals("user@example.com", a.value());
    }

    @Test
    void normalizesInternationalizedDomainToPunycode() {
        Email email = Email.of("usuario@mañana.example");
        assertEquals("usuario@xn--maana-pta.example", email.value());
    }

    @Test
    void rejectsInvalidEmails() {
        assertThrows(DomainException.class, () -> Email.of("not-an-email"));
        assertThrows(DomainException.class, () -> Email.of("@missing-local"));
        assertThrows(DomainException.class, () -> Email.of("missing-domain@"));
        assertThrows(DomainException.class, () -> Email.of(""));
        assertThrows(DomainException.class, () -> Email.of("   "));
    }
}