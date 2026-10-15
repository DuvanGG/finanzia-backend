package com.finanzia.application.config;

import com.finanzia.domain.common.TokenTtl;

import java.time.Duration;
import java.util.List;

/**
 * Configuración del dominio de autenticación. Inmutable; la capa de
 * infraestructura la construye desde properties.
 */
public record AuthSettings(
        String issuer,
        TokenTtl accessTokenTtl,
        TokenTtl refreshTokenTtl,
        Duration verifyCodeTtl,
        Duration resetCodeTtl,
        Duration refreshGraceWindow,
        Duration loginFailureWindow,
        int maxActiveSessions,
        List<LockoutThreshold> accountLockout,
        List<LockoutThreshold> ipLockout,
        int registerWindowSeconds,
        int registerLimit,
        int recoveryWindowSeconds,
        int recoveryLimit
) {

    public record LockoutThreshold(int failures, Duration lockFor) {
    }

    public static AuthSettings defaults() {
        return new AuthSettings(
                "finanzia",
                new TokenTtl(Duration.ofMinutes(15)),
                new TokenTtl(Duration.ofDays(7)),
                Duration.ofHours(24),
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                Duration.ofHours(1),
                10,
                List.of(new LockoutThreshold(5, Duration.ofMinutes(5)),
                        new LockoutThreshold(10, Duration.ofMinutes(15)),
                        new LockoutThreshold(15, Duration.ofHours(1))),
                List.of(new LockoutThreshold(10, Duration.ofMinutes(15))),
                60, 5,
                60, 5
        );
    }
}