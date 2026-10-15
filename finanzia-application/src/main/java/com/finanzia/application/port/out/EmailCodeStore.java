package com.finanzia.application.port.out;

import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacén de códigos de verificación/reset (outbound port). Solo el hash del
 * código se persiste.
 */
public interface EmailCodeStore {

    void save(EmailCode code);

    Optional<EmailCode> findByCodeHashAndPurpose(String codeHash, CodePurpose purpose);

    Optional<EmailCode> findLatestByUserAndPurpose(UserId userId, CodePurpose purpose);

    void markConsumed(UUID id, Instant at);

    void invalidateByUserAndPurpose(UserId userId, CodePurpose purpose, Instant at);
}