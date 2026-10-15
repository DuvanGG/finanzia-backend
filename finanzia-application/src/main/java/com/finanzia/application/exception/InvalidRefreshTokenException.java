package com.finanzia.application.exception;

/**
 * Refresh token inválido, expirado, revocado, o reuso detectado.
 */
public class InvalidRefreshTokenException extends AuthException {

    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}