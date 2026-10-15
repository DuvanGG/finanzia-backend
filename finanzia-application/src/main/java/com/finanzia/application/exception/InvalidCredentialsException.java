package com.finanzia.application.exception;

/**
 * Credenciales inválidas, respuesta genérica (no revela si el email existe).
 */
public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}