package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.LoginResult;
import com.finanzia.application.model.SessionInfo;
import com.finanzia.application.testutil.TestPorts.FakeClock;
import com.finanzia.application.testutil.TestPorts.FakeEmailCodeStore;
import com.finanzia.application.testutil.TestPorts.FakeEmailSender;
import com.finanzia.application.testutil.TestPorts.FakeLoginAttemptStore;
import com.finanzia.application.testutil.TestPorts.FakePasswordHasher;
import com.finanzia.application.testutil.TestPorts.FakeRefreshTokenStore;
import com.finanzia.application.testutil.TestPorts.FakeTokenProvider;
import com.finanzia.application.testutil.TestPorts.FakeUserRepository;
import com.finanzia.application.testutil.TestPorts.RecordingAuditLog;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagementServiceTest {

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
    private SessionManagementService management;

    @BeforeEach
    void setUp() {
        register = new RegisterUserService(users, hasher, codes, emailSender, attempts, audit, settings, clock);
        verify = new VerifyEmailService(users, codes, emailSender, attempts, audit, settings, clock);
        login = new LoginService(users, hasher, tokens, sessions, attempts, audit, settings, clock);
        management = new SessionManagementService(sessions, audit, clock);
    }

    private UserId registerVerifiedUser(String email) {
        register.register(email, PASSWORD, IP, "device", "c1");
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();
        verify.verify(code, "device", IP, "c2");
        return users.findByEmail(Email.of(email)).orElseThrow().id();
    }

    @Test
    void listReturnsOnlyActiveSessionsAndLogoutRemoves() {
        UserId id = registerVerifiedUser("alice@example.com");
        LoginResult login1 = login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        login.login("alice@example.com", PASSWORD, IP, "safari", "c4");

        List<SessionInfo> before = management.list(id, IP, "chrome", "c5");
        assertThat(before).hasSize(2);

        management.logout(login1.refreshToken().value(), IP, "chrome", "c6");

        List<SessionInfo> after = management.list(id, IP, "chrome", "c7");
        assertThat(after).hasSize(1);
    }

    @Test
    void revokeOthersSessionIsNotFound() {
        UserId alice = registerVerifiedUser("alice@example.com");
        registerVerifiedUser("bob@example.com");
        LoginResult bobLogin = login.login("bob@example.com", PASSWORD, IP, "chrome", "c3");

        assertThatThrownBy(() -> management.revokeSession(alice, bobLogin.sessionId(), IP, "chrome", "c4"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeOwnSessionWorks() {
        UserId alice = registerVerifiedUser("alice@example.com");
        LoginResult aliceLogin = login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");

        management.revokeSession(alice, aliceLogin.sessionId(), IP, "chrome", "c4");

        assertThat(sessions.findById(aliceLogin.sessionId()).orElseThrow().wasRevoked()).isTrue();
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.SESSION_REVOKED
                        && r.userId().equals(alice));
    }

    @Test
    void logoutAllRevokesEverySession() {
        UserId alice = registerVerifiedUser("alice@example.com");
        login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        login.login("alice@example.com", PASSWORD, IP, "safari", "c4");
        login.login("alice@example.com", PASSWORD, IP, "firefox", "c5");

        management.logoutAll(alice, IP, "chrome", "c6");

        assertThat(sessions.countActiveByUser(alice, clock.now())).isZero();
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.SESSION_REVOKED
                        && r.details().get("reason").equals("LOGOUT_ALL"));
    }

    @Test
    void revokingUnownedCleanupsAreNotFound() {
        UserId alice = registerVerifiedUser("alice@example.com");
        assertThatThrownBy(() -> management.revokeSession(alice,
                com.finanzia.domain.common.SessionId.from(UUID.randomUUID()), IP, "d", "cX"))
                .isInstanceOf(NotFoundException.class);
    }
}