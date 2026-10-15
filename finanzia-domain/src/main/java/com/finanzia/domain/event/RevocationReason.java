package com.finanzia.domain.event;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;

import java.time.Instant;

/**
 * Motivo de la revocación de una sesión o familia.
 */
public enum RevocationReason {
    LOGOUT,
    LOGOUT_ALL,
    DEVICE_REVOKE,
    MAX_SESSIONS,
    PASSWORD_CHANGE,
    PASSWORD_RESET,
    ACCOUNT_DISABLED
}