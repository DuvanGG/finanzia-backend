package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidRefreshTokenException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.LoginResult;
import com.finanzia.application.model.TokenPair;
import com.finanzia.application.testutil.TestPorts.FakeClock;
import com.finanzia.application.testutil.TestPorts.FakeEmailCodeStore;
import com.finanzia.application.testutil.TestPorts.FakeEmailSender;
import com.finanzia.application.testutil.TestPorts.FakeLoginAttemptStore;
import com.finanzia.application.testutil.TestPorts.FakePasswordHasher;
import com.finanzia.application.testutil.TestPorts.FakeRefreshTokenStore;
import com.finanzia.application.testutil.TestPorts.FakeTokenProvider;
import com.finanzia.application.testutil.TestPorts.FakeUserRepository;
import com.finanzia.application.testutil.TestPorts.RecordingAuditLog;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final IpAddress IP = IpAddress.of("1.2.3.4");
    private static final String PASSWORD = "This1s@StrongPass";

    private final FakeClock clock = new FakeClock(NOW);
    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final FakeTokenProvider tokens = new FakeTokenProvider();
    private final FakeRefreshTokenStore sessions = new FakeRefreshTokenStore();
    private final FakeEmailCodeStore codes = new FakeEmailCodeStore();
    private final FakeEmailSender emailSender = new FakeEmailSender();
    private final FakeLoginAttemptStore attempts = new FakeLoginAttemptStore(clock);
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final AuthSettings settings = AuthSettings.defaults();

    private RegisterUserService register;
    private VerifyEmailService verify;
    private LoginService login;
    private RefreshSessionService refresh;

    @BeforeEach
    void setUp() {
        register = new RegisterUserService(users, hasher, codes, emailSender, attempts, audit, settings, clock);
        verify = new VerifyEmailService(users, codes, emailSender, attempts, audit, settings, clock);
        login = new LoginService(users, hasher, tokens, sessions, attempts, audit, settings, clock);
        refresh = new RefreshSessionService(sessions, users, tokens, audit, settings, clock);
    }

    private LoginResult doLogin() {
        register.register("alice@example.com", PASSWORD, IP, "device", "c1");
        String body = emailSender.all().get(0).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();
        verify.verify(code, "device", IP, "c2");
        return login.login("alice@example.com", PASSWORD, IP, "device", "c3");
    }

    @Test
    void refreshRotatesTokenWithSameFamily() {
        LoginResult result = doLogin();
        String originalToken = result.refreshToken().value();
        String originalHash = Tokens.sha256Hex(originalToken);

        TokenPair pair = refresh.refresh(originalToken, "device", IP, "c4");

        assertThat(pair.refreshToken()).isNotNull();
        assertThat(pair.accessToken()).isNotNull();
        var oldSession = sessions.findByTokenHash(originalHash).orElseThrow();
        assertThat(oldSession.isConsumed()).isTrue();
        assertThat(audit.all())
                .filteredOn(r -> r.type() == AuditEventType.REFRESH_TOKEN_REUSED)
                .isEmpty();
    }

    @Test
    void concurrentReplayWithinGraceGetsValidPairWithoutRevocation() {
        LoginResult result = doLogin();
        String originalToken = result.refreshToken().value();

        TokenPair first = refresh.refresh(originalToken, "device", IP, "c4");

        clock.advance(Duration.ofSeconds(5));
        TokenPair second = refresh.refresh(originalToken, "device", IP, "c5");

        assertThat(first.refreshToken()).isNotNull();
        assertThat(second.refreshToken()).isNotNull();
        assertThat(audit.all())
                .filteredOn(r -> r.type() == AuditEventType.REFRESH_TOKEN_REUSED)
                .isEmpty();

        // la familia sigue intacta: el token devuelto por la primera llamada aún sirve
        clock.advance(Duration.ofSeconds(10));
        TokenPair third = refresh.refresh(first.refreshToken().value(), "device", IP, "c6");
        assertThat(third.refreshToken()).isNotNull();
    }

    @Test
    void replayOutsideGraceRevokesFamilyAndAudits() {
        LoginResult result = doLogin();
        String originalToken = result.refreshToken().value();

        TokenPair first = refresh.refresh(originalToken, "device", IP, "c4");

        clock.advance(settings.refreshGraceWindow().plus(Duration.ofSeconds(1)));
        assertThatThrownBy(() -> refresh.refresh(originalToken, "device", IP, "c5"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.REFRESH_TOKEN_REUSED);
        // toda la familia queda revocada
        assertThat(sessions.findByTokenHash(Tokens.sha256Hex(first.refreshToken().value())).orElseThrow()
                .wasRevoked()).isTrue();
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        LoginResult result = doLogin();
        clock.advance(settings.refreshTokenTtl().value().plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> refresh.refresh(result.refreshToken().value(), "device", IP, "c5"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThatThrownBy(() -> refresh.refresh("non-existent-token", "device", IP, "c5"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}