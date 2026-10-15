package com.finanzia.infrastructure.security;

import com.finanzia.application.service.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private static final String BCRYPT_REGEX = "\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}";

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(new BCryptPasswordEncoder(12));

    @Test
    void hashUsesCost12AndVerifies() {
        String hash = hasher.hash("This1s@StrongPass");
        assertThat(hash).matches(BCRYPT_REGEX);
        assertThat(hash).startsWith("$2a$12$");
        assertThat(hasher.matches("This1s@StrongPass", hash)).isTrue();
        assertThat(hasher.matches("WrongPassw0rd!", hash)).isFalse();
    }

    @Test
    void rejectsInvalidOrBlankHash() {
        assertThat(hasher.matches("whatever", "not-a-bcrypt-hash")).isFalse();
        assertThat(hasher.matches("whatever", "")).isFalse();
        assertThat(hasher.matches("whatever", null)).isFalse();
    }

    @Test
    void loginDummyHashIsAValidBcryptHash() {
        assertThat(LoginService.DUMMY_HASH).matches(BCRYPT_REGEX);
        assertThat(hasher.matches("anything-the-user-sends", LoginService.DUMMY_HASH)).isFalse();
    }
}