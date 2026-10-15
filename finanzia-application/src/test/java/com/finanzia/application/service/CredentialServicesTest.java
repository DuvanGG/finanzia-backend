package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidCodeException;
import com.finanzia.application.exception.InvalidCredentialsException;
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
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialServicesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final IpAddress IP = IpAddress.of("1.2.3.4");
    private static final String PASSWORD = "This1s@StrongPass";
    private static final String NEW_PASSWORD = "Another!StrongPass";

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
    private ChangePasswordService change;
    private ResetPasswordService reset;
    private DisableAccountService disable;

    @BeforeEach
    void setUp() {
        register = new RegisterUserService(users, hasher, codes, emailSender, attempts, audit, settings, clock);
        verify = new VerifyEmailService(users, codes, emailSender, attempts, audit, settings, clock);
        login = new LoginService(users, hasher, tokens, sessions, attempts, audit, settings, clock);
        change = new ChangePasswordService(users, hasher, sessions, audit, clock);
        reset = new ResetPasswordService(users, hasher, codes, emailSender, attempts, sessions, audit, settings, clock);
        disable = new DisableAccountService(users, sessions, audit, clock);
    }

    private UserId registerVerifiedUser(String email) {
        register.register(email, PASSWORD, IP, "device", "c1");
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();
        verify.verify(code, "device", IP, "c2");
        return users.findByEmail(Email.of(email)).orElseThrow().id();
    }

    // ----- Cambio de contraseña -----

    @Test
    void changePasswordRequiresCurrentPassword() {
        UserId alice = registerVerifiedUser("alice@example.com");
        assertThatThrownBy(() -> change.changePassword(alice, "WrongPassw0rd!", NEW_PASSWORD,
                com.finanzia.domain.common.SessionId.random(), IP, "d", "c"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changePasswordRevokesOtherSessionsAndKeepsCurrent() {
        UserId alice = registerVerifiedUser("alice@example.com");
        LoginResult sessionA = login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        login.login("alice@example.com", PASSWORD, IP, "safari", "c4");

        change.changePassword(alice, PASSWORD, NEW_PASSWORD, sessionA.sessionId(), IP, "chrome", "c5");

        assertThat(sessions.findById(sessionA.sessionId()).orElseThrow().isActiveAt(clock.now())).isTrue();
        assertThat(sessions.countActiveByUser(alice, clock.now())).isEqualTo(1);
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.CREDENTIALS_CHANGED
                        && r.details().get("reason").equals("PASSWORD_CHANGE"));

        // la nueva contraseña funciona
        LoginResult relogin = login.login("alice@example.com", NEW_PASSWORD, IP, "chrome", "c6");
        assertThat(relogin).isNotNull();
    }

    @Test
    void changePasswordRejectsOldPassword() {
        UserId alice = registerVerifiedUser("alice@example.com");
        LoginResult sessionA = login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        change.changePassword(alice, PASSWORD, NEW_PASSWORD, sessionA.sessionId(), IP, "chrome", "c4");

        assertThatThrownBy(() -> login.login("alice@example.com", PASSWORD, IP, "chrome", "c5"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ----- Recuperación de contraseña -----

    @Test
    void requestResetSendsCodeOnlyForVerifiedUsers() {
        registerVerifiedUser("alice@example.com");
        int before = emailSender.all().size();

        reset.requestReset("alice@example.com", "device", IP, "c3");

        assertThat(emailSender.all()).hasSize(before + 1);
    }

    @Test
    void requestResetUnknownEmailIsSilent() {
        reset.requestReset("nobody@example.com", "device", IP, "c3");
        assertThat(emailSender.all()).isEmpty();
    }

    @Test
    void resetPasswordWithValidCodeRevokesAllSessions() {
        UserId alice = registerVerifiedUser("alice@example.com");
        login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        login.login("alice@example.com", PASSWORD, IP, "safari", "c4");

        reset.requestReset("alice@example.com", "device", IP, "c5");
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();

        reset.resetPassword(code, NEW_PASSWORD, IP, "device", "c6");

        assertThat(sessions.countActiveByUser(alice, clock.now())).isZero();
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.CREDENTIALS_CHANGED
                        && r.details().get("reason").equals("PASSWORD_RESET"));
        login.login("alice@example.com", NEW_PASSWORD, IP, "chrome", "c7");
    }

    @Test
    void resetPasswordRejectsCodeUsedTwice() {
        UserId alice = registerVerifiedUser("alice@example.com");
        reset.requestReset("alice@example.com", "device", IP, "c3");
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        String code = body.substring(body.indexOf("es: ") + 4).trim();

        reset.resetPassword(code, NEW_PASSWORD, IP, "device", "c4");
        assertThatThrownBy(() -> reset.resetPassword(code, PASSWORD, IP, "device", "c5"))
                .isInstanceOf(InvalidCodeException.class);
    }

    @Test
    void resetPasswordRejectsInvalidCode() {
        registerVerifiedUser("alice@example.com");
        assertThatThrownBy(() -> reset.resetPassword("WRONGCODE", NEW_PASSWORD, IP, "d", "c"))
                .isInstanceOf(InvalidCodeException.class);
    }

    // ----- Deshabilitación -----

    @Test
    void disableAccountRevokesAllSessionsAndPreventsLogin() {
        UserId alice = registerVerifiedUser("alice@example.com");
        login.login("alice@example.com", PASSWORD, IP, "chrome", "c3");
        login.login("alice@example.com", PASSWORD, IP, "safari", "c4");

        disable.disable(alice, IP, "admin-console", "c5");

        assertThat(users.findById(alice).orElseThrow().isDisabled()).isTrue();
        assertThat(sessions.countActiveByUser(alice, clock.now())).isZero();
        assertThat(audit.all())
                .anyMatch(r -> r.type() == AuditEventType.USER_DISABLED);
        assertThatThrownBy(() -> login.login("alice@example.com", PASSWORD, IP, "chrome", "c6"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}