package com.finanzia.infrastructure.time;

import com.finanzia.application.port.out.Clock;

import java.time.Instant;

/**
 * Reloj del sistema ({@code Instant.now()}).
 */
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}