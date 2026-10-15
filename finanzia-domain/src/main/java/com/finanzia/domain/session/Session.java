package com.finanzia.domain.session;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Sesión / ledger de refresh token. Una familia encadena rotaciones; el reuso
 * de un token ya consumido (fuera de la ventana de gracia) revoca la familia.
 */
public class Session {

    private final SessionId id;
    private final UserId userId;
    private final TokenFamilyId familyId;
    private final String tokenHash;
    private final String device;
    private final IpAddress ip;
    private final Instant createdAt;
    private final Instant expiresAt;
    private SessionId rotatedTo;
    private Instant revokedAt;
    private Instant consumedAt;

    private Session(SessionId id, UserId userId, TokenFamilyId familyId, String tokenHash,
                    String device, IpAddress ip, Instant createdAt, Instant expiresAt,
                    SessionId rotatedTo, Instant revokedAt, Instant consumedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.device = device;
        this.ip = Objects.requireNonNull(ip, "ip");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.rotatedTo = rotatedTo;
        this.revokedAt = revokedAt;
        this.consumedAt = consumedAt;
    }

    public static Session create(SessionId id, UserId userId, TokenFamilyId familyId, String tokenHash,
                                 String device, IpAddress ip, Instant now, TokenTtl refreshTtl) {
        return new Session(id, userId, familyId, tokenHash, device, ip, now, now.plus(refreshTtl.value()),
                null, null, null);
    }

    public static Session existing(SessionId id, UserId userId, TokenFamilyId familyId, String tokenHash,
                                   String device, IpAddress ip, Instant createdAt, Instant expiresAt,
                                   SessionId rotatedTo, Instant revokedAt, Instant consumedAt) {
        return new Session(id, userId, familyId, tokenHash, device, ip, createdAt, expiresAt,
                rotatedTo, revokedAt, consumedAt);
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean wasConsumedWithin(Instant now, java.time.Duration graceWindow) {
        return consumedAt != null && !now.isAfter(consumedAt.plus(graceWindow));
    }

    public boolean wasRevoked() {
        return revokedAt != null;
    }

    public void consume(Instant at) {
        if (consumedAt != null) {
            return;
        }
        this.consumedAt = at;
    }

    public void attachSuccessor(SessionId successor) {
        if (this.rotatedTo != null) {
            throw new IllegalStateException("Session already has a successor");
        }
        this.rotatedTo = Objects.requireNonNull(successor, "successor");
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
        this.consumedAt = this.consumedAt != null ? this.consumedAt : at;
    }

    public SessionId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenFamilyId familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String device() {
        return device;
    }

    public IpAddress ip() {
        return ip;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public SessionId rotatedTo() {
        return rotatedTo;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}