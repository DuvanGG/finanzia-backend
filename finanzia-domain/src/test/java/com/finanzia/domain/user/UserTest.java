package com.finanzia.domain.user;

import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");
    private final PasswordHash hash = PasswordHash.of("hashed-value");

    private User newUser() {
        return User.register(UserId.random(), Email.of("user@example.com"), hash, now);
    }

    @Test
    void registrationCreatesActiveUserWithDefaultRole() {
        User user = newUser();
        assertEquals(Role.USER, user.role());
        assertTrue(user.isActive());
        assertFalse(user.hasVerifiedEmail());
    }

    @Test
    void disabledUserCannotBeActive() {
        User user = newUser();
        user.disable(now);
        assertTrue(user.isDisabled());
        assertFalse(user.isActive());
    }

    @Test
    void emailVerificationUpdatesStateOnce() {
        User user = newUser();
        user.markEmailVerified(now);
        assertTrue(user.hasVerifiedEmail());
        Instant stamped = user.emailVerifiedAt();
        user.markEmailVerified(now.plusSeconds(1));
        assertEquals(stamped, user.emailVerifiedAt());
    }

    @Test
    void changePasswordUpdatesHash() {
        User user = newUser();
        PasswordHash newHash = PasswordHash.of("other-hash");
        user.changePassword(newHash, now);
        assertEquals(newHash, user.passwordHash());
    }
}