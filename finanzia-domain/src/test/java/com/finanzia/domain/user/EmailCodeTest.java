package com.finanzia.domain.user;

import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailCodeTest {

    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void codeExpiresAndIsConsumedONce() {
        EmailCode code = EmailCode.create(UserId.random(), CodePurpose.VERIFY, "hash",
                now, Duration.ofHours(24));
        assertFalse(code.isExpiredAt(now.plus(Duration.ofHours(23))));
        assertTrue(code.isExpiredAt(now.plus(Duration.ofHours(25))));

        code.consume(now);
        assertTrue(code.isConsumed());
    }

    @Test
    void rejectsNullHash() {
        assertThrows(NullPointerException.class, () ->
                EmailCode.create(UserId.random(), CodePurpose.RESET, null, now, Duration.ofMinutes(15)));
    }
}