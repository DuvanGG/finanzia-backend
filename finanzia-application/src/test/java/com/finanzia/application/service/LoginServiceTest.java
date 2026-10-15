package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.AccountLockedException;
import com.finanzia.application.exception.EmailVerificationRequiredException;
import com.finanzia.application.exception.InvalidCredentialsException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.LoginResult;
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
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.event.RevocationReason;
import com.finanzia.domain.user.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest {

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

    private final RegisterUserService register = new RegisterUserService(users, hasher, codes, emailSender,
            attempts, audit, settings, clock);
    private final VerifyEmailService verify = new VerifyEmailService(users, codes, emailSender, attempts, audit,
            settings, clock);
    private final LoginService login = new LoginService(users, hasher, tokens, sessions, attempts, audit,
            settings, clock);

    private UserId registerVerifiedUser() {
        register.register("alice@example.com", PASSWORD, IP, "device", "c-reg");
        String body = emailSender.all().get(0).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();
        verify.verify(code, "device", IP, "c-verify");
        return users.findByEmail(Email.of("alice@example.com")).orElseThrow().id();
    }

    @Test
    void loginSuccessIssuesPairAndStoresRefreshHash() {
        UserId id = registerVerifiedUser();

        LoginResult result = login.login("alice@example.com", PASSWORD, IP, "chrome", "c1");

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();
        assertThat(result.sessionId()).isNotNull();
        assertThat(result.userId()).isEqualTo(id);
        assertThat(result.roles()).containsExactly(Role.USER);

        assertThat(sessions.findByTokenHash(Tokens.sha256Hex(result.refreshToken().value()))).isPresent();
        assertThat(sessions.findByTokenHash(result.refreshToken().value())).isEmpty();
        assertThat(audit.all()).anyMatch(r -> r.type() == AuditEventType.LOGIN_SUCCESS);
    }

    @Test
    void wrongPasswordThrowsInvalidCredentials() {
        registerVerifiedUser();
        assertThatThrownBy(() -> login.login("alice@example.com", "WrongPassw0rd!", IP, "d", "c1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownEmailThrowsInvalidCredentialsAndDoesNotCreateAccountLockout() {
        assertThatThrownBy(() -> login.login("nobody@example.com", PASSWORD, IP, "d", "c1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(audit.all())
                .filteredOn(r -> r.type() == AuditEventType.ACCOUNT_LOCKED)
                .isEmpty();
    }

    @Test
    void unverifiedEmailThrowsVerificationRequired() {
        register.register("bob@example.com", PASSWORD, IP, "d", "c1");
        assertThatThrownBy(() -> login.login("bob@example.com", PASSWORD, IP, "d", "c2"))
                .isInstanceOf(EmailVerificationRequiredException.class);
    }

    @Test
    void disabledUserLoginFailsGeneric() {
        UserId id = registerVerifiedUser();
        users.findById(id).orElseThrow().disable(NOW);

        assertThatThrownBy(() -> login.login("alice@example.com", PASSWORD, IP, "d", "c1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void accountLocksAfterThresholdThenThrowsAccountLocked() {
        registerVerifiedUser();
        int threshold = settings.accountLockout().get(0).failures();
        for (int i = 0; i < threshold; i++) {
            String corr = "c" + i;
            assertThatThrownBy(() -> login.login("alice@example.com", "WrongPassw0rd!", IP, "d", corr))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> login.login("alice@example.com", PASSWORD, IP, "d", "c-lock"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void ipBlockedAfterUnknownLoginFlood() {
        int threshold = settings.ipLockout().get(0).failures();
        for (int i = 0; i < threshold; i++) {
            String email = "nobody" + i + "@example.com";
            String corr = "c" + i;
            assertThatThrownBy(() -> login.login(email, PASSWORD, IP, "d", corr))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> login.login("newuser@example.com", PASSWORD, IP, "d", "c-block"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void activeSessionLimitRevokesOldest() {
        registerVerifiedUser();
        for (int i = 0; i < settings.maxActiveSessions() + 1; i++) {
            login.login("alice@example.com", PASSWORD, IP, "device" + (i % 7), "c" + i);
        }
        UserId id = users.findByEmail(Email.of("alice@example.com")).orElseThrow().id();
        assertThat(sessions.countActiveByUser(id, clock.now()))
                .isEqualTo(settings.maxActiveSessions());
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.SESSION_REVOKED
                        && RevocationReason.MAX_SESSIONS.name().equals(r.details().get("reason")));
    }
}