package com.finanzia;

import com.finanzia.application.port.out.EmailSender;
import com.finanzia.domain.common.Email;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EmailSender de prueba que captura los correos enviados para poder extraer los
 * códigos de verificación/reset en los tests end-to-end.
 */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE_PATTERN = Pattern.compile("(?:^|\\D)([A-HJ-NP-Z2-9]{8})(?:\\D|$)");

    private final List<Mail> sent = new ArrayList<>();

    public record Mail(Email to, String subject, String body) {
    }

    @Override
    public synchronized void send(Email to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public synchronized List<Mail> sent() {
        return List.copyOf(sent);
    }

    public synchronized String lastCodeFor(Email to, String subjectPart) {
        return sent().stream()
                .filter(mail -> mail.to().equals(to) && mail.subject().contains(subjectPart))
                .reduce((first, second) -> second)
                .map(Mail::body)
                .map(this::extractCode)
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró código para " + to + " con asunto " + subjectPart));
    }

    private String extractCode(String body) {
        Matcher matcher = CODE_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("No se encontró código en: " + body);
    }
}