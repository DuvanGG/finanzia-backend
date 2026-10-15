package com.finanzia.application.port.out;

import com.finanzia.domain.common.Email;

/**
 * Envío de correos (outbound port). Implementación SES/SMTP en infraestructura.
 */
public interface EmailSender {

    void send(Email to, String subject, String body);
}