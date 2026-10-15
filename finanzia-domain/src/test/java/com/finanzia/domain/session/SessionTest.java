package com.finanzia.domain.session;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");
    private final UserId userId = UserId.random();
    private final TokenFamilyId family = TokenFamilyId.random();

    private Session activeSession() {
        return Session.create(SessionId.random(), userId, family, "abc123hash", "browser",
                IpAddress.of("10.0.0.1"), now, TokenTtl.of(Duration.ofDays(7)));
    }

    @Test
    void sessionIsActiveInsideValidityInterval() {
        Session session = activeSession();
        assertTrue(session.isActiveAt(now.plusSeconds(1)));
        assertFalse(session.isActiveAt(now.plus(Duration.ofDays(7))));
    }

    @Test
    void revokedSessionIsNotActive() {
        Session session = activeSession();
        session.revoke(now);
        assertFalse(session.isActiveAt(now));
        assertTrue(session.wasRevoked());
    }

    @Test
    void consumeMarksTokenUsed() {
        Session session = activeSession();
        session.consume(now);
        assertTrue(session.isConsumed());
        assertTrue(session.wasConsumedWithin(now.plusSeconds(10), Duration.ofSeconds(30)));
        assertFalse(session.wasConsumedWithin(now.plus(Duration.ofMinutes(1)), Duration.ofSeconds(30)));
    }

    @Test
    void successorIsAttachedOnce() {
        Session session = activeSession();
        session.attachSuccessor(SessionId.random());
        assertTrue(session.rotatedTo() != null);
    }

    @Test
    void revokeAfterConsumePreservesReuseDetection() {
        Session session = activeSession();
        session.consume(now);
        session.revoke(now);
        assertTrue(session.isConsumed());
        assertTrue(session.wasRevoked());
    }
}