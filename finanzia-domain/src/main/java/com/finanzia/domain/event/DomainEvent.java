package com.finanzia.domain.event;

import com.finanzia.domain.common.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Base de los eventos de dominio relacionados con seguridad.
 */
public abstract class DomainEvent {

    private final Instant occurredAt;
    private final UserId userId;

    protected DomainEvent(Instant occurredAt, UserId userId) {
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.userId = userId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * Identificador del usuario asociado, cuando se conoce (nulo en intentos
     * sobre emails desconocidos).
     */
    public UserId userId() {
        return userId;
    }
}