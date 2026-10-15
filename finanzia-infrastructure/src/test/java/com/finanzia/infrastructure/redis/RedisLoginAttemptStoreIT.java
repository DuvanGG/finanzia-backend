package com.finanzia.infrastructure.redis;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración del estado efímero de intentos contra Redis real (Testcontainers).
 */
@Testcontainers
class RedisLoginAttemptStoreIT {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static StringRedisTemplate template;
    private final RedisLoginAttemptStore store = new RedisLoginAttemptStore(template);

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterEach
    void flush() {
        template.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void incrementsFailuresWithWindow() {
        UserId user = UserId.random();
        IpAddress ip = IpAddress.of("10.0.0.1");

        store.incrementFailures(user, ip, Duration.ofMinutes(5));
        store.incrementFailures(user, ip, Duration.ofMinutes(5));

        assertThat(store.failureCount(user)).isEqualTo(2);
        assertThat(store.ipFailureCount(ip)).isEqualTo(2);
    }

    @Test
    void lockAndBlockExposeRemainingTtl() {
        UserId user = UserId.random();
        IpAddress ip = IpAddress.of("10.0.0.2");

        store.lockAccount(user, Duration.ofMinutes(10));
        store.blockIp(ip, Duration.ofMinutes(10));

        assertThat(store.isAccountLocked(user)).isTrue();
        assertThat(store.isIpBlocked(ip)).isTrue();
        assertThat(store.accountLockRemaining(user)).isPresent();
        assertThat(store.ipBlockRemaining(ip)).isPresent();
    }

    @Test
    void rateLimitAllowsUpToLimitThenDenies() {
        IpAddress ip = IpAddress.of("10.0.0.3");
        Duration window = Duration.ofMinutes(1);

        assertThat(store.isWithinLimit(ip, "register", 2, window)).isTrue();
        assertThat(store.isWithinLimit(ip, "register", 2, window)).isTrue();
        assertThat(store.isWithinLimit(ip, "register", 2, window)).isFalse();
    }

    @Test
    void clearRemovesCountersAndLock() {
        UserId user = UserId.random();
        IpAddress ip = IpAddress.of("10.0.0.4");
        store.incrementFailures(user, ip, Duration.ofMinutes(5));
        store.lockAccount(user, Duration.ofMinutes(5));

        store.clear(user, ip);

        assertThat(store.failureCount(user)).isZero();
        assertThat(store.ipFailureCount(ip)).isZero();
        assertThat(store.isAccountLocked(user)).isFalse();
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        RedisLoginAttemptStore deadStore = new RedisLoginAttemptStore(deadTemplate);

        assertThat(deadStore.isAccountLocked(UserId.random())).isFalse();
        assertThat(deadStore.isWithinLimit(IpAddress.of("10.0.0.5"), "login", 5, Duration.ofMinutes(1))).isTrue();
        assertThat(deadStore.failureCount(UserId.random())).isZero();
    }
}