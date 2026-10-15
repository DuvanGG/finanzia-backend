package com.finanzia.infrastructure.persistence;

import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.session.Session;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración de los adaptadores JPA contra Postgres real (Testcontainers).
 * Verifica mapeo y las operaciones atómicas de rotación/revocación.
 */
@SpringBootTest(classes = PersistenceIT.TestConfig.class)
@Testcontainers
class PersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.finanzia.infrastructure.persistence.entity")
    @EnableJpaRepositories(basePackages = "com.finanzia.infrastructure.persistence.repository")
    static class TestConfig {

        @org.springframework.context.annotation.Bean
        JpaUserRepository jpaUserRepository(
                com.finanzia.infrastructure.persistence.repository.UserJpaRepository jpa) {
            return new JpaUserRepository(jpa);
        }

        @org.springframework.context.annotation.Bean
        JpaSessionStore jpaSessionStore(
                com.finanzia.infrastructure.persistence.repository.SessionJpaRepository jpa) {
            return new JpaSessionStore(jpa);
        }

        @org.springframework.context.annotation.Bean
        JpaEmailCodeStore jpaEmailCodeStore(
                com.finanzia.infrastructure.persistence.repository.EmailCodeJpaRepository jpa) {
            return new JpaEmailCodeStore(jpa);
        }
    }

    @Autowired
    JpaUserRepository userRepo;
    @Autowired
    JpaSessionStore sessionStore;
    @Autowired
    JpaEmailCodeStore emailCodeStore;

    @Test
    void userMappingRoundTrip() {
        JpaUserRepository repo = userRepo;
        User user = User.register(UserId.random(), Email.of("User@Example.com"),
                PasswordHash.of("HASH"), Instant.parse("2026-01-01T00:00:00Z"));

        repo.save(user);

        User loaded = repo.findByEmail(Email.of("user@example.com")).orElseThrow();
        assertThat(loaded.id()).isEqualTo(user.id());
        assertThat(loaded.passwordHash().value()).isEqualTo("HASH");
        assertThat(loaded.role()).isEqualTo(user.role());
        assertThat(loaded.hasVerifiedEmail()).isFalse();

        loaded.markEmailVerified(Instant.parse("2026-01-02T00:00:00Z"));
        repo.save(loaded);
        assertThat(repo.findById(user.id()).orElseThrow().hasVerifiedEmail()).isTrue();
    }

    @Test
    void rotationIsAtomicAndLoserDoesNotLeaveOrphans() {
        JpaSessionStore store = sessionStore;
        JpaUserRepository userRepo = this.userRepo;
        UserId userId = UserId.random();
        userRepo.save(User.register(userId, Email.of("rot@example.com"), PasswordHash.of("H"), Instant.now()));

        TokenFamilyId family = TokenFamilyId.random();
        Session first = Session.create(SessionId.random(), userId, family, "hash-1", "device",
                IpAddress.of("1.2.3.4"), Instant.now(), new TokenTtl(Duration.ofDays(7)));
        store.save(first);

        Session successorA = Session.create(SessionId.random(), userId, family, "hash-2", "device",
                IpAddress.of("1.2.3.4"), Instant.now(), new TokenTtl(Duration.ofDays(7)));
        store.save(successorA);

        assertThat(store.tryConsumeAndAttach("hash-1", successorA.id(), Instant.now())).isTrue();

        Session successorB = Session.create(SessionId.random(), userId, family, "hash-3", "device",
                IpAddress.of("1.2.3.4"), Instant.now(), new TokenTtl(Duration.ofDays(7)));
        store.save(successorB);

        assertThat(store.tryConsumeAndAttach("hash-1", successorB.id(), Instant.now())).isFalse();

        assertThat(store.findByTokenHash("hash-1").orElseThrow().isConsumed()).isTrue();
        assertThat(store.findByTokenHash("hash-1").orElseThrow().rotatedTo()).isEqualTo(successorA.id());
        assertThat(store.findById(successorB.id())).isEmpty();
        assertThat(store.findByTokenHash("hash-3")).isEmpty();
    }

    @Test
    void revokeFamilyRevokesEveryRowInFamily() {
        JpaSessionStore store = sessionStore;
        UserId userId = UserId.random();
        userRepo.save(User.register(userId, Email.of("fam@example.com"), PasswordHash.of("H"), Instant.now()));
        TokenFamilyId family = TokenFamilyId.random();
        Session a = Session.create(SessionId.random(), userId, family, "fam-a", "d", IpAddress.of("1.1.1.1"),
                Instant.now(), new TokenTtl(Duration.ofDays(7)));
        Session b = Session.create(SessionId.random(), userId, family, "fam-b", "d", IpAddress.of("1.1.1.1"),
                Instant.now(), new TokenTtl(Duration.ofDays(7)));
        store.save(a);
        store.save(b);

        store.revokeFamily(family, Instant.now());

        assertThat(store.findByTokenHash("fam-a").orElseThrow().wasRevoked()).isTrue();
        assertThat(store.findByTokenHash("fam-b").orElseThrow().wasRevoked()).isTrue();
        assertThat(store.countActiveByUser(userId, Instant.now())).isZero();
    }

    @Test
    void revokeSessionsExceptKeepsCurrentAndPurgingRemovesDeadRows() {
        JpaSessionStore store = sessionStore;
        UserId userId = UserId.random();
        userRepo.save(User.register(userId, Email.of("purge@example.com"), PasswordHash.of("H"), Instant.now()));
        Session keep = Session.create(SessionId.random(), userId, TokenFamilyId.random(), "keep", "d",
                IpAddress.of("1.1.1.1"), Instant.now(), new TokenTtl(Duration.ofDays(7)));
        Session other = Session.create(SessionId.random(), userId, TokenFamilyId.random(), "other", "d",
                IpAddress.of("1.1.1.1"), Instant.now(), new TokenTtl(Duration.ofDays(7)));
        store.save(keep);
        store.save(other);

        store.revokeSessionsExcept(userId, keep.id(), Instant.now());
        assertThat(store.findByTokenHash("keep").orElseThrow().wasRevoked()).isFalse();
        assertThat(store.findByTokenHash("other").orElseThrow().wasRevoked()).isTrue();

        store.revoke(keep.id(), Instant.now());
        int purged = store.purgeForUser(userId, Instant.now());
        assertThat(purged).isEqualTo(2);
        assertThat(store.findById(keep.id())).isEmpty();
    }

    @Test
    void emailCodeStoreRoundTrip() {
        JpaEmailCodeStore store = emailCodeStore;
        UserId userId = UserId.random();
        userRepo.save(User.register(userId, Email.of("code@example.com"), PasswordHash.of("H"), Instant.now()));
        var code = com.finanzia.domain.user.EmailCode.create(userId,
                com.finanzia.domain.user.CodePurpose.VERIFY, "hash-code", Instant.now(), Duration.ofHours(24));
        store.save(code);

        assertThat(store.findByCodeHashAndPurpose("hash-code", com.finanzia.domain.user.CodePurpose.VERIFY))
                .isPresent();
        store.markConsumed(code.id(), Instant.now());
        assertThat(store.findByCodeHashAndPurpose("hash-code", com.finanzia.domain.user.CodePurpose.VERIFY)
                .orElseThrow().isConsumed()).isTrue();
    }
}