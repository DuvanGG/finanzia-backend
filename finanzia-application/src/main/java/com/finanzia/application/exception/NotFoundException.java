package com.finanzia.application.exception;

/**
 * Recurso no encontrado para el usuario autenticado (IDs de otros usuarios o
 * inexistentes se comportan igual, sin revelar existencia).
 */
public class NotFoundException extends AuthException {

    public NotFoundException() {
        super("Not found");
    }
}