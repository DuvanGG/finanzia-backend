package com.finanzia.api.web;

import com.finanzia.api.web.dto.ApiError;
import com.finanzia.application.exception.AccountLockedException;
import com.finanzia.application.exception.EmailVerificationRequiredException;
import com.finanzia.application.exception.InvalidCodeException;
import com.finanzia.application.exception.InvalidCredentialsException;
import com.finanzia.application.exception.InvalidRefreshTokenException;
import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.exception.RateLimitedException;
import com.finanzia.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones del dominio/aplicación a respuestas HTTP uniformes
 * (sin filtrar detalles sensibles).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiError> unauthorized(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_CREDENTIALS", "Credenciales inválidas"));
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ApiError> verificationRequired(EmailVerificationRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("EMAIL_NOT_VERIFIED", "Debe verificar su email antes de iniciar sesión"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> locked(AccountLockedException ex) {
        long retryAfter = Math.max(0, ex.remaining().toSeconds());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                .body(ApiError.of("ACCOUNT_LOCKED", "Cuenta bloqueada temporalmente"));
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiError> rateLimited(RateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of("RATE_LIMITED", "Demasiadas peticiones, intente más tarde"));
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<ApiError> invalidCode(InvalidCodeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_CODE", "Código inválido, expirado o ya utilizado"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", "No tiene permisos para esta operación"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "Recurso no encontrado"));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> validation(WebExchangeBindException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(field -> details.put(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Datos de entrada inválidos", details));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> badInput(ServerWebInputException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", "Cuerpo de la petición inválido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Error no controlado en {}: {}", exchange.getRequest().getPath(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Error interno"));
    }
}