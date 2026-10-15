package com.finanzia.infrastructure.bootstrap;

import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.Password;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.Role;
import com.finanzia.domain.user.User;
import com.finanzia.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Siembra el primer usuario ADMIN al arrancar, a partir de las variables
 * {@code ADMIN_EMAIL}/{@code ADMIN_PASSWORD}. Si ya existe o no están
 * definidas, no hace nada.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository users, PasswordHasher hasher, Clock clock,
                       @Value("${finanzia.admin.email:}") String adminEmail,
                       @Value("${finanzia.admin.password:}") String adminPassword) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Seed de admin omitido (ADMIN_EMAIL/ADMIN_PASSWORD no definidos)");
            return;
        }
        Email email = Email.of(adminEmail);
        if (users.findByEmail(email).isPresent()) {
            log.info("Admin '{}' ya existe; seed omitido", email.value());
            return;
        }
        new Password(adminPassword);
        Instant now = clock.now();
        User admin = User.existing(UserId.random(), email, PasswordHash.of(hasher.hash(adminPassword)),
                Role.ADMIN, UserStatus.ACTIVE, now, now, now);
        users.save(admin);
        log.info("Admin inicial '{}' creado", email.value());
    }
}