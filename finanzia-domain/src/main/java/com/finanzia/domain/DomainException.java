package com.finanzia.domain;

/**
 * Excepción base de la reglas de negocio del dominio.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}