package com.finanzia.infrastructure.security;

import com.finanzia.domain.DomainException;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.DecodedAccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "finanzia";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, ISSUER, "kid-test");

    @Test
    void issueAndDecodeRoundTrip() {
        Instant now = Instant.now();
        UserId userId = UserId.random();
        SessionId sessionId = SessionId.random();

        AccessToken token = provider.issueAccessToken(userId, sessionId, Set.of(Role.USER),
                now, new TokenTtl(Duration.ofMinutes(15)), ISSUER);
        DecodedAccessToken decoded = provider.decode(token.value());

        assertThat(decoded.userId()).isEqualTo(userId);
        assertThat(decoded.sessionId()).isEqualTo(sessionId);
        assertThat(decoded.roles()).containsExactly(Role.USER);
        assertThat(decoded.jwtId()).isNotNull();
        assertThat(decoded.issuedAt()).isCloseTo(now, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void rejectsExpiredToken() {
        Instant longAgo = Instant.now().minus(Duration.ofHours(3));
        AccessToken expired = provider.issueAccessToken(UserId.random(), SessionId.random(), Set.of(Role.USER),
                longAgo, new TokenTtl(Duration.ofMinutes(15)), ISSUER);

        assertThatThrownBy(() -> provider.decode(expired.value()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsTamperedToken() {
        AccessToken token = provider.issueAccessToken(UserId.random(), SessionId.random(), Set.of(Role.ADMIN),
                Instant.now(), new TokenTtl(Duration.ofMinutes(15)), ISSUER);

        assertThatThrownBy(() -> provider.decode(token.value() + "x"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenProvider other = new JwtTokenProvider("ffffffffffffffffffffffffffffffff", ISSUER, "kid");
        AccessToken token = other.issueAccessToken(UserId.random(), SessionId.random(), Set.of(Role.USER),
                Instant.now(), new TokenTtl(Duration.ofMinutes(15)), ISSUER);

        assertThatThrownBy(() -> provider.decode(token.value()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void issueRefreshTokenIsUniqueAndOpaque() {
        RefreshToken a = provider.issueRefreshToken(Instant.now(), new TokenTtl(Duration.ofDays(7)));
        RefreshToken b = provider.issueRefreshToken(Instant.now(), new TokenTtl(Duration.ofDays(7)));

        assertThat(a.value()).isNotEqualTo(b.value());
        assertThat(a.value()).doesNotStartWith("eyJ");
        assertThat(a.value().length()).isGreaterThan(40);
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret", ISSUER, "kid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}