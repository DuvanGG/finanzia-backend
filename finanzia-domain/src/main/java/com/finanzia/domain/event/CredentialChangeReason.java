package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Instant;

/**
 * Cambio de credenciales (contraseña).
 */
public enum CredentialChangeReason {
    PASSWORD_CHANGE,
    PASSWORD_RESET
}