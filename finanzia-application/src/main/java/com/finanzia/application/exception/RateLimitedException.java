package com.finanzia.application.exception;

/**
 * Tasa de peticiones excedida (throttling).
 */
public class RateLimitedException extends AuthException {

    public RateLimitedException() {
        super("Too many requests");
    }
}