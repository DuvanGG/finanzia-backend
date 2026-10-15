package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.application.port.out.EmailSender;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.util.Codes;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;
import com.finanzia.domain.user.Password;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Registro de un nuevo usuario local. Crea la cuenta (rol USER, inactiva hasta
 * verificar email) y envía un código de verificación de un solo uso.
 * Si el email ya existe la respuesta es idéntica (no revela cuentas ni envía
 * correos), pero el hash ya se ejecutó para igualar el tiempo de cómputo.
 */
public class RegisterUserService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final EmailCodeStore codes;
    private final EmailSender emailSender;
    private final LoginAttemptStore attempts;
    private final AuditLog audit;
    private final AuthSettings settings;
    private final Clock clock;

    public RegisterUserService(UserRepository users, PasswordHasher hasher, EmailCodeStore codes,
                               EmailSender emailSender, LoginAttemptStore attempts, AuditLog audit,
                               AuthSettings settings, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.codes = codes;
        this.emailSender = emailSender;
        this.attempts = attempts;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    public static final String GENERIC_RESPONSE = "Se recibió la solicitud. Si el email no estaba registrado, "
            + "se enviará un código de verificación.";

    public String register(String emailRaw, String passwordRaw, IpAddress ip, String device, String correlationId) {
        if (!attempts.isWithinLimit(ip, "REGISTER", settings.registerLimit(),
                Duration.ofSeconds(settings.registerWindowSeconds()))) {
            throw new RateLimitedException();
        }
        Email email = Email.of(emailRaw);
        new Password(passwordRaw);
        hasher.hash(passwordRaw);
        Instant now = clock.now();

        if (users.findByEmail(email).isPresent()) {
            return GENERIC_RESPONSE;
        }

        User user = User.register(UserId.random(), email, PasswordHash.of(hasher.hash(passwordRaw)), now);
        users.save(user);

        String code = Codes.generate(8);
        EmailCode emailCode = EmailCode.create(user.id(), CodePurpose.VERIFY, Tokens.sha256Hex(code), now,
                settings.verifyCodeTtl());
        codes.save(emailCode);
        emailSender.send(user.email(), "Verifica tu email en FinanzIA",
                "Tu código de verificación es: " + code);

        audit.record(new AuditRecord(now, AuditEventType.USER_REGISTERED, AuditOutcome.SUCCESS, user.id(),
                ip, device, correlationId, Map.of("email", email.value()), "Usuario registrado"));

        return GENERIC_RESPONSE;
    }
}