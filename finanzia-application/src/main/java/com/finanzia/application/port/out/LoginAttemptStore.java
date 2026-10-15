package com.finanzia.application.port.out;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;

import java.time.Duration;
import java.util.Optional;

/**
 * Contadores de intentos fallidos / lockout (outbound port). Estado efímero en
 * Redis. Fail-open: ante fallo del adaptador, los chequeos "no bloquean".
 */
public interface LoginAttemptStore {

    boolean isAccountLocked(UserId userId);

    boolean isIpBlocked(IpAddress ip);

    void incrementFailures(UserId userId, IpAddress ip, Duration window);

    /**
     * Solo contador por IP (para emails desconocidos, sin usuario asociado).
     */
    void incrementIpFailures(IpAddress ip, Duration window);

    long failureCount(UserId userId);

    long ipFailureCount(IpAddress ip);

    void lockAccount(UserId userId, Duration duration);

    void blockIp(IpAddress ip, Duration duration);

    Optional<Duration> accountLockRemaining(UserId userId);

    Optional<Duration> ipBlockRemaining(IpAddress ip);

    void clear(UserId userId, IpAddress ip);

    /**
     * Throttling genérico por IP (registro/recovery).
     *
     * @return true si la acción está dentro del límite; false si debe rechazarse.
     */
    boolean isWithinLimit(IpAddress ip, String action, int limit, Duration window);
}