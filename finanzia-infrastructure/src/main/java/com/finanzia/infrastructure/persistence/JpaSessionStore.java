package com.finanzia.infrastructure.persistence;

import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.session.Session;
import com.finanzia.infrastructure.persistence.entity.SessionJpaEntity;
import com.finanzia.infrastructure.persistence.repository.SessionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA del ledger {@code sessions}. La rotación se hace con un UPDATE
 * condicional (atómico por fila) que solo marca consumido/atacha sucesor si la
 * fila no estaba consumida, revocada ni expirada.
 */
@Component
public class JpaSessionStore implements RefreshTokenStore {

    private static final String UNKNOWN_IP = "unknown";

    private final SessionJpaRepository jpa;

    public JpaSessionStore(SessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(Session session) {
        jpa.save(toJpa(session));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findById(SessionId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean tryConsumeAndAttach(String tokenHash, SessionId successorId, Instant now) {
        Optional<SessionJpaEntity> successor = jpa.findById(successorId.value());
        if (successor.isEmpty()) {
            return false;
        }
        int updated = jpa.consumeAndAttach(tokenHash, successorId.value(), now);
        if (updated == 0) {
            jpa.delete(successor.get());
        }
        return updated == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> listExpiredOrRevoked() {
        return jpa.findExpiredOrRevoked(Instant.now()).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean deleteRow(SessionId id) {
        if (jpa.existsById(id.value())) {
            jpa.deleteById(id.value());
            return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findActiveByTokenHash(String tokenHash, Instant now) {
        return jpa.findByTokenHash(tokenHash)
                .filter(e -> e.getRevokedAt() == null && now.isBefore(e.getExpiresAt()))
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void revoke(SessionId id, Instant now) {
        jpa.markRevoked(id.value(), now);
    }

    @Override
    @Transactional
    public void revokeFamily(TokenFamilyId familyId, Instant now) {
        jpa.revokeFamily(familyId.value(), now);
    }

    @Override
    @Transactional
    public void revokeAllByUser(UserId userId, Instant now) {
        jpa.revokeAllByUser(userId.value(), now);
    }

    @Override
    @Transactional
    public void revokeSessionsExcept(UserId userId, SessionId keep, Instant now) {
        jpa.revokeSessionsExcept(userId.value(), keep.value(), now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> listActiveByUser(UserId userId, Instant now) {
        return jpa.findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThan(userId.value(), now)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveByUser(UserId userId, Instant now) {
        return (int) jpa.countByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThan(userId.value(), now);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findOldestActiveByUser(UserId userId, Instant now) {
        return jpa.findFirstByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByCreatedAtAsc(userId.value(), now)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public int purgeForUser(UserId userId, Instant now) {
        return jpa.purgeExpiredOrRevoked(userId.value(), now);
    }

    private Session toDomain(SessionJpaEntity e) {
        String ip = e.getIp() == null ? UNKNOWN_IP : e.getIp();
        return Session.existing(
                SessionId.from(e.getId()),
                UserId.from(e.getUserId()),
                TokenFamilyId.from(e.getFamilyId()),
                e.getTokenHash(),
                e.getDevice(),
                IpAddress.of(ip),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getRotatedTo() == null ? null : SessionId.from(e.getRotatedTo()),
                e.getRevokedAt(),
                e.getConsumedAt());
    }

    private SessionJpaEntity toJpa(Session s) {
        SessionJpaEntity e = new SessionJpaEntity();
        e.setId(s.id().value());
        e.setUserId(s.userId().value());
        e.setFamilyId(s.familyId().value());
        e.setTokenHash(s.tokenHash());
        e.setDevice(s.device());
        e.setIp(s.ip().value());
        e.setCreatedAt(s.createdAt());
        e.setExpiresAt(s.expiresAt());
        e.setRotatedTo(s.rotatedTo() == null ? null : s.rotatedTo().value());
        e.setRevokedAt(s.revokedAt());
        e.setConsumedAt(s.consumedAt());
        return e;
    }
}