package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.testutil.TestPorts.FakeClock;
import com.finanzia.application.testutil.TestPorts.FakeEmailCodeStore;
import com.finanzia.application.testutil.TestPorts.FakeEmailSender;
import com.finanzia.application.testutil.TestPorts.FakeLoginAttemptStore;
import com.finanzia.application.testutil.TestPorts.FakePasswordHasher;
import com.finanzia.application.testutil.TestPorts.FakeUserRepository;
import com.finanzia.application.testutil.TestPorts.RecordingAuditLog;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.user.CodePurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final IpAddress IP = IpAddress.of("1.2.3.4");

    private final FakeClock clock = new FakeClock(NOW);
    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final FakeEmailCodeStore codes = new FakeEmailCodeStore();
    private final FakeEmailSender emailSender = new FakeEmailSender();
    private final FakeLoginAttemptStore attempts = new FakeLoginAttemptStore(clock);
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final AuthSettings settings = AuthSettings.defaults();

    private final RegisterUserService service = new RegisterUserService(users, hasher, codes, emailSender,
            attempts, audit, settings, clock);

    @Test
    void registerCreatesUserStoresHashedCodeAndSendsEmail() {
        String result = service.register("Alice@Example.com", "This1s@StrongPass", IP, "test-device", "cor-1");

        assertThat(result).isEqualTo(RegisterUserService.GENERIC_RESPONSE);
        var created = users.findByEmail(Email.of("alice@example.com")).orElseThrow();
        assertThat(created.hasVerifiedEmail()).isFalse();

        assertThat(emailSender.all()).hasSize(1);
        String code = emailSender.all().get(0).body().substring(emailSender.all().get(0).body().indexOf("es: ") + 4).trim();
        assertThat(codes.findByCodeHashAndPurpose(Tokens.sha256Hex(code), CodePurpose.VERIFY))
                .isPresent();
        assertThat(codes.findByCodeHashAndPurpose(code, CodePurpose.VERIFY))
                .isEmpty();

        assertThat(audit.all()).hasSize(1);
        assertThat(audit.all().get(0).type()).isEqualTo(AuditEventType.USER_REGISTERED);
        assertThat(audit.all().get(0).userId()).isEqualTo(created.id());
        assertThat(audit.all().get(0).details()).containsEntry("email", "alice@example.com");
    }

    @Test
    void duplicateEmailIsGenericAndDoesNotSendAnotherEmail() {
        service.register("alice@example.com", "This1s@StrongPass", IP, "d", "c1");
        int sentAfterFirst = emailSender.all().size();
        int usersAfterFirst = users.findAll().size();

        String result = service.register("alice@example.com", "This1s@StrongPass", IP, "d", "c2");

        assertThat(result).isEqualTo(RegisterUserService.GENERIC_RESPONSE);
        assertThat(emailSender.all()).hasSize(sentAfterFirst);
        assertThat(users.findAll()).hasSize(usersAfterFirst);
    }

    @Test
    void exceedingRegisterLimitThrowsRateLimited() {
        for (int i = 0; i < settings.registerLimit(); i++) {
            service.register("user" + i + "@example.com", "This1s@StrongPass", IP, "d", "c" + i);
        }
        assertThatThrownBy(() -> service.register("last@example.com", "This1s@StrongPass", IP, "d", "cl"))
                .isInstanceOf(RateLimitedException.class);
    }
}