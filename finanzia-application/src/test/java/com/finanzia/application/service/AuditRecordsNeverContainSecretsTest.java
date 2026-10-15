package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.model.AuditRecord;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garantía de seguridad: los registros de auditoría jamás contienen contraseñas
 * en claro, refresh tokens ni códigos de verificación (solo hashes y metadatos).
 */
class AuditRecordsNeverContainSecretsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final IpAddress IP = IpAddress.of("9.9.9.9");
    private static final String PASSWORD = "This1s@PrivateP455";
    private static final String NEW_PASSWORD = "Another1!PrivateP455";

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
    private ChangePasswordService change;
    private ResetPasswordService reset;
    private DisableAccountService disable;

    private String lastEmailCode() {
        String body = emailSender.all().get(emailSender.all().size() - 1).body();
        return body.substring(body.indexOf("es: ") + 4).trim();
    }

    @BeforeEach
    void setUp() {
        register = new RegisterUserService(users, hasher, codes, emailSender, attempts, audit, settings, clock);
        verify = new VerifyEmailService(users, codes, emailSender, attempts, audit, settings, clock);
        login = new LoginService(users, hasher, tokens, sessions, attempts, audit, settings, clock);
        refresh = new RefreshSessionService(sessions, users, tokens, audit, settings, clock);
        change = new ChangePasswordService(users, hasher, sessions, audit, clock);
        reset = new ResetPasswordService(users, hasher, codes, emailSender, attempts, sessions, audit, settings, clock);
        disable = new DisableAccountService(users, sessions, audit, clock);
    }

    @Test
    void auditLogNeverContainsSecretsAcrossFullLifecycle() {
        register.register("secret@example.com", PASSWORD, IP, "device", "c1");
        String verifyCode = lastEmailCode();
        verify.verify(verifyCode, "device", IP, "c2");

        var loginResult = login.login("secret@example.com", PASSWORD, IP, "chrome", "c3");
        String originalRefresh = loginResult.refreshToken().value();
        String access = loginResult.accessToken().value();

        var tokenPair = refresh.refresh(originalRefresh, "chrome", IP, "c4");
        String rotatedRefresh = tokenPair.refreshToken().value();

        change.changePassword(loginResult.userId(), PASSWORD, NEW_PASSWORD, loginResult.sessionId(), IP, "chrome", "c5");

        reset.requestReset("secret@example.com", "device", IP, "c6");
        String resetCode = lastEmailCode();
        reset.resetPassword(resetCode, NEW_PASSWORD, IP, "device", "c7");

        login.login("secret@example.com", NEW_PASSWORD, IP, "chrome", "c8");

        java.util.List<java.util.Set<String>> senses = java.util.List.of(
                java.util.Set.of(PASSWORD, NEW_PASSWORD, originalRefresh, rotatedRefresh, access, verifyCode, resetCode));

        for (AuditRecord record : audit.all()) {
            String blob = blob(record);
            for (var group : senses) {
                assertThat(blob)
                        .as("audit record %s must not leak secrets", record.type())
                        .doesNotContain(group.toArray(new String[0]));
            }
        }
    }

    private String blob(AuditRecord record) {
        StringBuilder sb = new StringBuilder(record.type().name())
                .append(record.outcome())
                .append(record.description() == null ? "" : record.description());
        for (Map.Entry<String, String> e : record.details().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    @Test
    void userIdsAreNeverSecretMetadata() {
        register.register("meta@example.com", PASSWORD, IP, "device", "c1");
        assertThat(audit.all()).hasSize(1);
        AuditRecord r = audit.all().get(0);
        assertThat(r.userId()).isInstanceOf(UserId.class);
        assertThat(r.details().get("email")).isEqualTo("meta@example.com");
    }
}