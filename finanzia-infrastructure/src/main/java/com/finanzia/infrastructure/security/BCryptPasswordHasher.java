package com.finanzia.infrastructure.security;

import com.finanzia.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashing BCrypt con coste 12 (outbound port). Fluxión: coste alto para
 * ralentizar ataques offline sobre la tabla de hash.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    /**
     * @param encoder encoder Spring Security configurado con coste 12.
     */
    public BCryptPasswordHasher(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    @Override
    public boolean matches(String plainPassword, String encodedHash) {
        if (encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        try {
            return encoder.matches(plainPassword, encodedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}