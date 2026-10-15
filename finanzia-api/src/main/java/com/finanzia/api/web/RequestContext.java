package com.finanzia.api.web;

import com.finanzia.domain.common.IpAddress;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;
import java.util.UUID;

/**
 * Extrae la información de cliente (IP, dispositivo, correlación) del request.
 */
public final class RequestContext {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_CORRELATION_ID = "X-Correlation-Id";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_DEVICE_LENGTH = 255;

    private RequestContext() {
    }

    public static IpAddress ip(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        String raw = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> address.getAddress().getHostAddress())
                        .orElse(UNKNOWN);
        return IpAddress.of(raw);
    }

    public static String device(ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        return userAgent.length() > MAX_DEVICE_LENGTH ? userAgent.substring(0, MAX_DEVICE_LENGTH) : userAgent;
    }

    public static String correlationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(X_CORRELATION_ID);
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}