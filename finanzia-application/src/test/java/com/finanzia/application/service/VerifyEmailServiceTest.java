package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidCodeException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.testutil.TestPorts.FakeClock;
import com.finanzia.application.testutil.TestPorts.FakeEmailCodeStore;
import com.finanzia.application.testutil.TestPorts.FakeEmailSender;
import com.finanzia.application.testutil.TestPorts.FakeLoginAttemptStore;
import com.finanzia.application.testutil.TestPorts.FakeUserRepository;
import com.finanzia.application.testutil.TestPorts.RecordingAuditLog;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifyEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final IpAddress IP = IpAddress.of("1.2.3.4");

    private final FakeClock clock = new FakeClock(NOW);
    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeEmailCodeStore codes = new FakeEmailCodeStore();
    private final FakeEmailSender emailSender = new FakeEmailSender();
    private final FakeLoginAttemptStore attempts = new FakeLoginAttemptStore(clock);
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final AuthSettings settings = AuthSettings.defaults();

    private final RegisterUserService register = new RegisterUserService(users, new com.finanzia.application.testutil.TestPorts.FakePasswordHasher(),
            codes, emailSender, attempts, audit, settings, clock);
    private final VerifyEmailService verify = new VerifyEmailService(users, codes, emailSender, attempts, audit, settings, clock);

    private String latestCode() {
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        return body.substring(body.indexOf("es: ") + 4).trim();
    }

    @Test
    void verifyConsumesCodeAndMarksEmailVerified() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        String code = latestCode();

        verify.verify(code, "d", IP, "c2");

        var user = users.findByEmail(Email.of("alice@example.com")).orElseThrow();
        assertThat(user.hasVerifiedEmail()).isTrue();
        var stored = codes.findByCodeHashAndPurpose(Tokens.sha256Hex(code), com.finanzia.domain.user.CodePurpose.VERIFY);
        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow().isConsumed()).isTrue();
    }

    @Test
    void invalidCodeThrows() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        assertThatThrownBy(() -> verify.verify("WRONGCODE", "d", IP, "c2"))
                .isInstanceOf(InvalidCodeException.class);
    }

    @Test
    void expiredCodeThrows() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        String code = latestCode();
        clock.advance(Duration.ofHours(25));

        assertThatThrownBy(() -> verify.verify(code, "d", IP, "c2"))
                .isInstanceOf(InvalidCodeException.class);
        var user = users.findByEmail(Email.of("alice@example.com")).orElseThrow();
        assertThat(user.hasVerifiedEmail()).isFalse();
    }

    @Test
    void consumedCodeThrows() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        String code = latestCode();
        verify.verify(code, "d", IP, "c2");

        assertThatThrownBy(() -> verify.verify(code, "d", IP, "c3"))
                .isInstanceOf(InvalidCodeException.class);
    }

    @Test
    void resendInvalidatesOldCodeAndSendsNew() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        String oldCode = latestCode();

        verify.resend("alice@example.com", IP, "d", "c2");

        assertThat(emailSender.all()).hasSize(2);
        var oldStored = codes.findByCodeHashAndPurpose(Tokens.sha256Hex(oldCode), com.finanzia.domain.user.CodePurpose.VERIFY).orElseThrow();
        assertThat(oldStored.isConsumed()).isTrue();

        String newCode = latestCode();
        verify.verify(newCode, "d", IP, "c3");
        assertThat(users.findByEmail(Email.of("alice@example.com")).orElseThrow().hasVerifiedEmail()).isTrue();
    }

    @Test
    void resendForUnknownEmailIsSilent() {
        verify.resend("nobody@example.com", IP, "d", "c1");
        assertThat(emailSender.all()).isEmpty();
    }

    @Test
    void resendTooFrequentThrowsRateLimited() {
        for (int i = 0; i < settings.recoveryLimit(); i++) {
            verify.resend("nobody@example.com", IP, "d", "c" + i);
        }
        assertThatThrownBy(() -> verify.resend("nobody@example.com", IP, "d", "c_last"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void resendForAlreadyVerifiedEmailDoesNothing() {
        register.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        verify.verify(latestCode(), "d", IP, "c2");

        verify.resend("alice@example.com", IP, "d", "c3");

        assertThat(emailSender.all()).hasSize(1);
    }
}