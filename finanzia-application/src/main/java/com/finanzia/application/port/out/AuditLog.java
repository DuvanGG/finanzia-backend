package com.finanzia.application.port.out;

import com.finanzia.application.model.AuditRecord;

/**
 * Registro de auditoría de seguridad (outbound port). Append-only; nunca
 * contiene secretos.
 */
public interface AuditLog {

    void record(AuditRecord record);
}