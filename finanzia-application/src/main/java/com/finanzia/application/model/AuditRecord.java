package com.finanzia.application.model;

import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;

import java.time.Instant;
import java.util.Map;

/**
 * Registro de auditoría de seguridad. Nunca contiene passwords, tokens ni hashes.
 */
public record AuditRecord(
        Instant occurredAt,
        AuditEventType type,
        AuditOutcome outcome,
        UserId userId,
        IpAddress ip,
        String device,
        String correlationId,
        Map<String, String> details,
        String description
) {
}