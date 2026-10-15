package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidCodeException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.application.port.out.EmailSender;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.util.Codes;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;
import com.finanzia.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Verificación del email mediante código de un solo uso y reenvío de códigos.
 * Códigos inválidos, expirados o ya consumidos responden igual (sin revelar).
 */
public class VerifyEmailService {

    private final UserRepository users;
    private final EmailCodeStore codes;
    private final EmailSender emailSender;
    private final LoginAttemptStore attempts;
    private final AuditLog audit;
    private final AuthSettings settings;
    private final Clock clock;

    public VerifyEmailService(UserRepository users, EmailCodeStore codes, EmailSender emailSender,
                              LoginAttemptStore attempts, AuditLog audit, AuthSettings settings, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.emailSender = emailSender;
        this.attempts = attempts;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    public void verify(String code, String device, IpAddress ip, String correlationId) {
        Instant now = clock.now();
        EmailCode emailCode = codes.findByCodeHashAndPurpose(Tokens.sha256Hex(code), CodePurpose.VERIFY)
                .orElseThrow(InvalidCodeException::new);
        if (emailCode.isConsumed() || emailCode.isExpiredAt(now)) {
            throw new InvalidCodeException();
        }
        User user = users.findById(emailCode.userId()).orElseThrow(InvalidCodeException::new);
        if (user.isDisabled()) {
            throw new InvalidCodeException();
        }
        user.markEmailVerified(now);
        users.save(user);
        codes.markConsumed(emailCode.id(), now);

        audit.record(new AuditRecord(now, AuditEventType.EMAIL_VERIFIED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of(), "Email verificado"));
    }

    public void resend(String emailRaw, IpAddress ip, String device, String correlationId) {
        if (!attempts.isWithinLimit(ip, "EMAIL_RESEND", settings.recoveryLimit(),
                Duration.ofSeconds(settings.recoveryWindowSeconds()))) {
            throw new RateLimitedException();
        }
        Email email;
        try {
            email = Email.of(emailRaw);
        } catch (RuntimeException e) {
            return;
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.hasVerifiedEmail() || user.isDisabled()) {
            return;
        }
        Instant now = clock.now();
        codes.invalidateByUserAndPurpose(user.id(), CodePurpose.VERIFY, now);
        String code = Codes.generate(8);
        EmailCode emailCode = EmailCode.create(user.id(), CodePurpose.VERIFY, Tokens.sha256Hex(code), now,
                settings.verifyCodeTtl());
        codes.save(emailCode);
        emailSender.send(user.email(), "Verifica tu email en FinanzIA",
                "Tu código de verificación es: " + code);
    }
}