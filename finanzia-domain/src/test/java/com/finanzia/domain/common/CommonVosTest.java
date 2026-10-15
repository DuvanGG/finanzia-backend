package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonVosTest {

    @Test
    void userIdsAreEqualByValue() {
        UUID raw = UUID.randomUUID();
        assertEquals(UserId.from(raw), UserId.from(raw));
        assertNotEquals(UserId.random(), UserId.random());
    }

    @Test
    void sessionAndFamilyIdsWrapUuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(SessionId.from(raw), new SessionId(raw));
        assertEquals(TokenFamilyId.from(raw), new TokenFamilyId(raw));
        assertEquals(JwtId.from(raw), new JwtId(raw));
    }

    @Test
    void assertsRejectNullAndMalformed() {
        assertThrows(NullPointerException.class, () -> UserId.from((UUID) null));
        assertThrows(NullPointerException.class, () -> new SessionId(null));
        assertThrows(NullPointerException.class, () -> new TokenFamilyId(null));
        assertThrows(DomainException.class, () -> UserId.from("not-a-uuid"));
        assertThrows(DomainException.class, () -> JwtId.from("not-a-uuid"));
    }

    @Test
    void ipAddressMustNotBeBlank() {
        assertEquals("192.168.1.1", IpAddress.of("192.168.1.1").value());
        assertThrows(DomainException.class, () -> IpAddress.of("  "));
        assertThrows(DomainException.class, () -> IpAddress.of(null));
    }

    @Test
    void tokenTtlMustBePositive() {
        assertEquals(java.time.Duration.ofMinutes(15),
                TokenTtl.of(java.time.Duration.ofMinutes(15)).value());
        assertThrows(DomainException.class, () -> TokenTtl.of(java.time.Duration.ZERO));
        assertThrows(DomainException.class, () -> TokenTtl.of(java.time.Duration.ofMinutes(-1)));
    }
}