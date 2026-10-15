package com.finanzia.infrastructure.persistence;

import com.finanzia.application.port.out.UserRepository;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.PasswordHash;
import com.finanzia.domain.user.User;
import com.finanzia.infrastructure.persistence.entity.UserJpaEntity;
import com.finanzia.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adaptador JPA para {@link UserRepository}. Mapeo explícito (sin ORM leak).
 */
@Component
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    public JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        jpa.save(toJpa(user));
        return user;
    }

    private User toDomain(UserJpaEntity e) {
        return User.existing(
                UserId.from(e.getId()),
                Email.of(e.getEmail()),
                PasswordHash.of(e.getPasswordHash()),
                e.getRole(),
                e.getStatus(),
                e.getEmailVerifiedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private UserJpaEntity toJpa(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(user.id().value());
        e.setEmail(user.email().value());
        e.setPasswordHash(user.passwordHash().value());
        e.setRole(user.role());
        e.setStatus(user.status());
        e.setEmailVerifiedAt(user.emailVerifiedAt());
        e.setCreatedAt(user.createdAt());
        e.setUpdatedAt(user.updatedAt());
        return e;
    }
}