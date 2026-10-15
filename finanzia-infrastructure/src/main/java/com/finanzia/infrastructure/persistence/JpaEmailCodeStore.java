package com.finanzia.infrastructure.persistence;

import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;
import com.finanzia.infrastructure.persistence.entity.EmailCodeJpaEntity;
import com.finanzia.infrastructure.persistence.repository.EmailCodeJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@link EmailCodeStore}. Solo almacena el hash del código.
 */
@Component
public class JpaEmailCodeStore implements EmailCodeStore {

    private final EmailCodeJpaRepository jpa;

    public JpaEmailCodeStore(EmailCodeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(EmailCode code) {
        jpa.save(toJpa(code));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailCode> findByCodeHashAndPurpose(String codeHash, CodePurpose purpose) {
        return jpa.findFirstByCodeHashAndPurposeOrderByCreatedAtDesc(codeHash, purpose).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailCode> findLatestByUserAndPurpose(UserId userId, CodePurpose purpose) {
        return jpa.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId.value(), purpose).map(this::toDomain);
    }

    @Override
    @Transactional
    public void markConsumed(UUID id, Instant at) {
        jpa.findById(id).ifPresent(e -> {
            e.setConsumedAt(at);
            jpa.save(e);
        });
    }

    @Override
    @Transactional
    public void invalidateByUserAndPurpose(UserId userId, CodePurpose purpose, Instant at) {
        jpa.invalidateByUserAndPurpose(userId.value(), purpose, at);
    }

    private EmailCode toDomain(EmailCodeJpaEntity e) {
        return new EmailCode(
                e.getId(),
                UserId.from(e.getUserId()),
                e.getPurpose(),
                e.getCodeHash(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getConsumedAt());
    }

    private EmailCodeJpaEntity toJpa(EmailCode c) {
        EmailCodeJpaEntity e = new EmailCodeJpaEntity();
        e.setId(c.id() != null ? c.id() : UUID.randomUUID());
        e.setUserId(c.userId().value());
        e.setCodeHash(c.codeHash());
        e.setPurpose(c.purpose());
        e.setCreatedAt(c.createdAt());
        e.setExpiresAt(c.expiresAt());
        e.setConsumedAt(c.consumedAt());
        return e;
    }
}