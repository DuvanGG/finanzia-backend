package com.finanzia.application.port.out;

/**
 * Hashing y verificación de contraseñas (outbound port). Coste y algoritmo
 * configurados por el adaptador (BCrypt cost 12).
 */
public interface PasswordHasher {

    String hash(String plainPassword);

    boolean matches(String plainPassword, String encodedHash);
}