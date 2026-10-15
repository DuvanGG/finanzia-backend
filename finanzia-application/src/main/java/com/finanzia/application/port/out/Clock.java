package com.finanzia.application.port.out;

import java.time.Instant;

/**
 * Reloj de la aplicación (outbound port) para expiraciones/lockouts testables.
 */
public interface Clock {

    Instant now();
}