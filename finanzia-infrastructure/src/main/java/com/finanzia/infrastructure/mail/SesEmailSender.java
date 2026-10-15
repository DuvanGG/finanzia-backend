package com.finanzia.infrastructure.mail;

import com.finanzia.application.port.out.EmailSender;
import com.finanzia.domain.common.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Envío de correos vía AWS SES (LocalStack en desarrollo). El remitente puede
 * requerir verificación en SES real; en LocalStack se acepta cualquiera.
 */
public class SesEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SesEmailSender.class);

    private final SesClient client;
    private final String from;

    public SesEmailSender(SesClient client, String from) {
        this.client = client;
        this.from = from;
    }

    @Override
    public void send(Email to, String subject, String body) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(from)
                .destination(Destination.builder().toAddresses(to.value()).build())
                .message(Message.builder()
                        .subject(Content.builder().charset("UTF-8").data(subject).build())
                        .body(Body.builder()
                                .text(Content.builder().charset("UTF-8").data(body).build())
                                .build())
                        .build())
                .build();
        client.sendEmail(request);
        log.debug("Email sent to {}", to);
    }
}