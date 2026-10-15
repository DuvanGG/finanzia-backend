package com.finanzia.application.exception;

/**
 * Código de verificación/reset inválido, expirado o ya utilizado.
 */
public class InvalidCodeException extends AuthException {

    public InvalidCodeException() {
        super("Invalid, expired or already-used code");
    }
}