package com.finanzia.infrastructure.persistence.repository;

import com.finanzia.infrastructure.persistence.entity.SessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Operaciones sobre el ledger {@code sessions}. Las actualizaciones atómicas
 * (consumo/rotación) son sentencias UPDATE condicionales a nivel de fila.
 */
public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update SessionJpaEntity s
               set s.consumedAt = :now, s.rotatedTo = :successorId
             where s.tokenHash = :tokenHash
               and s.consumedAt is null
               and s.revokedAt is null
               and s.expiresAt > :now
            """)
    int consumeAndAttach(@Param("tokenHash") String tokenHash,
                         @Param("successorId") UUID successorId,
                         @Param("now") Instant now);

    List<SessionJpaEntity> findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThan(UUID userId, Instant now);

    Optional<SessionJpaEntity> findFirstByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderByCreatedAtAsc(
            UUID userId, Instant now);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThan(UUID userId, Instant now);

    @Query("select s from SessionJpaEntity s where s.revokedAt is not null or s.expiresAt <= :now")
    List<SessionJpaEntity> findExpiredOrRevoked(@Param("now") Instant now);

    @Modifying
    @Query("update SessionJpaEntity s set s.revokedAt = :now where s.id = :id")
    int markRevoked(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update SessionJpaEntity s set s.revokedAt = :now where s.familyId = :family and s.revokedAt is null")
    int revokeFamily(@Param("family") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update SessionJpaEntity s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update SessionJpaEntity s set s.revokedAt = :now
             where s.userId = :userId and s.revokedAt is null and s.id <> :keep
            """)
    int revokeSessionsExcept(@Param("userId") UUID userId, @Param("keep") UUID keep, @Param("now") Instant now);

    @Modifying
    @Query("""
            delete from SessionJpaEntity s
             where s.userId = :userId and (s.revokedAt is not null or s.expiresAt <= :now)
            """)
    int purgeExpiredOrRevoked(@Param("userId") UUID userId, @Param("now") Instant now);
}