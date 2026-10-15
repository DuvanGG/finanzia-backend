package com.finanzia.application.port.out;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ledger de sesiones / refresh tokens (outbound port). Provee las operaciones
 * atómicas necesarias para rotación, reuso y revocación.
 */
public interface RefreshTokenStore {

    void save(Session session);

    Optional<Session> findById(SessionId id);

    Optional<Session> findByTokenHash(String tokenHash);

    /**
     * Consumo atómico de un refresh token. Actualiza la fila solo si aún no fue
     * consumida y no está revocada ni expirada; enlaza el sucesor.
     *
     * @return true si se consumió esta llamada; false si otro hilo la ganó (o revocada/expirada).
     */
    boolean tryConsumeAndAttach(String tokenHash, SessionId successorId, Instant now);

    List<Session> listExpiredOrRevoked();
    boolean deleteRow(SessionId id);

    Optional<Session> findActiveByTokenHash(String tokenHash, Instant now);

    void revoke(SessionId id, Instant now);

    void revokeFamily(TokenFamilyId familyId, Instant now);

    void revokeAllByUser(UserId userId, Instant now);

    void revokeSessionsExcept(UserId userId, SessionId keep, Instant now);

    List<Session> listActiveByUser(UserId userId, Instant now);

    int countActiveByUser(UserId userId, Instant now);

    Optional<Session> findOldestActiveByUser(UserId userId, Instant now);

    /**
     * Purga perezosa de sesiones expiradas o revocadas de un usuario.
     * @return número de filas eliminadas.
     */
    int purgeForUser(UserId userId, Instant now);
}