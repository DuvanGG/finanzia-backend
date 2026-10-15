package com.finanzia.domain.user;

/**
 * Estado del ciclo de vida de la cuenta. Una cuenta {@code DISABLED} no puede
 * iniciar sesión y tiene todas sus sesiones revocadas.
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}