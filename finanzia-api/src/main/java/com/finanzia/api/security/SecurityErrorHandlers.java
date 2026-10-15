package com.finanzia.api.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Respuestas JSON 401/403 coherentes con el resto del API. Serializa manualmente
 * para no depender de un bean {@code ObjectMapper} en el arranque de seguridad.
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {
    }

    public static ServerAuthenticationEntryPoint unauthorized() {
        return (exchange, ex) -> write(exchange, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Autenticación requerida");
    }

    public static ServerAccessDeniedHandler forbidden() {
        return (exchange, ex) -> write(exchange, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "No tiene permisos para esta operación");
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}