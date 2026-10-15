package com.finanzia.domain.user;

import com.finanzia.domain.common.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Código de verificación/reset enviado por email. Solo se persiste su hash.
 * Generación de código en claro y hash por {@link #create} y el puerto de
 * hashing del lado de aplicación.
 */
public class EmailCode {

    private final UUID id;
    private final UserId userId;
    private final CodePurpose purpose;
    private final String codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant consumedAt;

    public EmailCode(UUID id, UserId userId, CodePurpose purpose, String codeHash,
                     Instant createdAt, Instant expiresAt, Instant consumedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.consumedAt = consumedAt;
    }

    public static EmailCode create(UserId userId, CodePurpose purpose, String codeHash,
                                   Instant now, java.time.Duration ttl) {
        return new EmailCode(UUID.randomUUID(), userId, purpose, codeHash, now, now.plus(ttl), null);
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt) || now.equals(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void consume(Instant at) {
        this.consumedAt = at;
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public CodePurpose purpose() {
        return purpose;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}