package com.finanzia.domain.user;

import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root del usuario. Mantiene credenciales (solo hash), estado de
 * verificación, rol y estado de la cuenta. Sus reglas no dependen de Spring.
 */
public class User {

    private final UserId id;
    private final Email email;
    private PasswordHash passwordHash;
    private Role role;
    private UserStatus status;
    private Instant emailVerifiedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, PasswordHash passwordHash, Role role,
                 UserStatus status, Instant emailVerifiedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static User register(UserId id, Email email, PasswordHash passwordHash, Instant now) {
        return new User(id, email, passwordHash, Role.USER, UserStatus.ACTIVE, null, now);
    }

    public static User existing(UserId id, Email email, PasswordHash passwordHash, Role role,
                                UserStatus status, Instant emailVerifiedAt, Instant createdAt,
                                Instant updatedAt) {
        User user = new User(id, email, passwordHash, role, status, emailVerifiedAt, createdAt);
        user.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        return user;
    }

    public boolean hasVerifiedEmail() {
        return emailVerifiedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    public void markEmailVerified(Instant now) {
        if (hasVerifiedEmail()) {
            return;
        }
        this.emailVerifiedAt = now;
        this.updatedAt = now;
    }

    public void changePassword(PasswordHash newHash, Instant now) {
        Objects.requireNonNull(newHash, "newHash");
        this.passwordHash = newHash;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof User other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}