package com.finanzia.application.exception;

/**
 * Email aún no verificado: se debe verificar antes de poder iniciar sesión.
 */
public class EmailVerificationRequiredException extends AuthException {

    public EmailVerificationRequiredException() {
        super("Email verification required");
    }
}