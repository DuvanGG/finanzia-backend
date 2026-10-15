package com.finanzia.application.model;

/**
 * Resultado de tipo de evento de auditoría.
 */
public enum AuditEventType {
    USER_REGISTERED,
    EMAIL_VERIFIED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    REFRESH_TOKEN_REUSED,
    SESSION_REVOKED,
    CREDENTIALS_CHANGED,
    USER_DISABLED
}