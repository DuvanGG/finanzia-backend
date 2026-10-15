package com.finanzia.infrastructure.persistence.repository;

import com.finanzia.domain.user.CodePurpose;
import com.finanzia.infrastructure.persistence.entity.EmailCodeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailCodeJpaRepository extends JpaRepository<EmailCodeJpaEntity, UUID> {

    Optional<EmailCodeJpaEntity> findFirstByCodeHashAndPurposeOrderByCreatedAtDesc(String codeHash, CodePurpose purpose);

    Optional<EmailCodeJpaEntity> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, CodePurpose purpose);

    @Modifying
    @Query("""
            update EmailCodeJpaEntity e set e.consumedAt = :at
             where e.userId = :userId and e.purpose = :purpose and e.consumedAt is null
            """)
    int invalidateByUserAndPurpose(@Param("userId") UUID userId,
                                   @Param("purpose") CodePurpose purpose,
                                   @Param("at") Instant at);
}