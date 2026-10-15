package com.finanzia.infrastructure.redis;

import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Contadores de intentos fallidos / lockout / throttling en Redis.
 * <p>
 * Fail-open: si Redis no responde, los chequeos "no bloquean" (se logea warning)
 * para no tirar toda la autenticación con un fallo externo.
 */
@Component
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptStore.class);

    private static final String KEY_ACCOUNT_LOCK = "auth:lock:acct:%s";
    private static final String KEY_IP_BLOCK = "auth:lock:ip:%s";
    private static final String KEY_ACCOUNT_FAILURES = "auth:fail:acct:%s";
    private static final String KEY_IP_FAILURES = "auth:fail:ip:%s";
    private static final String KEY_RATE = "auth:rate:%s:%s";

    private final StringRedisTemplate redis;

    public RedisLoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isAccountLocked(UserId userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(String.format(KEY_ACCOUNT_LOCK, userId)));
        } catch (RuntimeException e) {
            return failOpen(e, false);
        }
    }

    @Override
    public boolean isIpBlocked(IpAddress ip) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(String.format(KEY_IP_BLOCK, ip.value())));
        } catch (RuntimeException e) {
            return failOpen(e, false);
        }
    }

    @Override
    public void incrementFailures(UserId userId, IpAddress ip, Duration window) {
        try {
            incrementWithExpire(KEY_ACCOUNT_FAILURES.formatted(userId), window);
            incrementWithExpire(KEY_IP_FAILURES.formatted(ip.value()), window);
        } catch (RuntimeException e) {
            log.warn("Redis incrementFailures failed: {}", e.getMessage());
        }
    }

    @Override
    public void incrementIpFailures(IpAddress ip, Duration window) {
        try {
            incrementWithExpire(KEY_IP_FAILURES.formatted(ip.value()), window);
        } catch (RuntimeException e) {
            log.warn("Redis incrementIpFailures failed: {}", e.getMessage());
        }
    }

    @Override
    public long failureCount(UserId userId) {
        try {
            return count(String.format(KEY_ACCOUNT_FAILURES, userId));
        } catch (RuntimeException e) {
            return failOpen(e, 0L);
        }
    }

    @Override
    public long ipFailureCount(IpAddress ip) {
        try {
            return count(String.format(KEY_IP_FAILURES, ip.value()));
        } catch (RuntimeException e) {
            return failOpen(e, 0L);
        }
    }

    @Override
    public void lockAccount(UserId userId, Duration duration) {
        try {
            redis.opsForValue().set(String.format(KEY_ACCOUNT_LOCK, userId), "1",
                    duration.toSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis lockAccount failed: {}", e.getMessage());
        }
    }

    @Override
    public void blockIp(IpAddress ip, Duration duration) {
        try {
            redis.opsForValue().set(String.format(KEY_IP_BLOCK, ip.value()), "1",
                    duration.toSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("Redis blockIp failed: {}", e.getMessage());
        }
    }

    @Override
    public Optional<Duration> accountLockRemaining(UserId userId) {
        try {
            return remaining(String.format(KEY_ACCOUNT_LOCK, userId));
        } catch (RuntimeException e) {
            return failOpen(e, Optional.empty());
        }
    }

    @Override
    public Optional<Duration> ipBlockRemaining(IpAddress ip) {
        try {
            return remaining(String.format(KEY_IP_BLOCK, ip.value()));
        } catch (RuntimeException e) {
            return failOpen(e, Optional.empty());
        }
    }

    @Override
    public void clear(UserId userId, IpAddress ip) {
        try {
            redis.delete(java.util.List.of(
                    String.format(KEY_ACCOUNT_LOCK, userId),
                    String.format(KEY_ACCOUNT_FAILURES, userId),
                    String.format(KEY_IP_FAILURES, ip.value())));
        } catch (RuntimeException e) {
            log.warn("Redis clear failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isWithinLimit(IpAddress ip, String action, int limit, Duration window) {
        try {
            String key = String.format(KEY_RATE, action, ip.value());
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redis.expire(key, window.toSeconds(), TimeUnit.SECONDS);
            }
            return value != null && value <= limit;
        } catch (RuntimeException e) {
            return failOpen(e, true);
        }
    }

    private void incrementWithExpire(String key, Duration window) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, window.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Optional<Duration> remaining(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return redis.hasKey(key) ? Optional.of(Duration.ZERO) : Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(ttl));
    }

    private <T> T failOpen(RuntimeException e, T defaultValue) {
        log.warn("Redis unavailable, failing open (default {}): {}", defaultValue, e.getMessage());
        return defaultValue;
    }
}