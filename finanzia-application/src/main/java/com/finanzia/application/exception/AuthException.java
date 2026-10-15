package com.finanzia.application.exception;

/**
 * Base de los errores de autenticación/autorización de la aplicación.
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}