package com.finanzia.api.web;

import com.finanzia.api.web.dto.SessionResponse;
import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.service.SessionManagementService;
import com.finanzia.domain.common.SessionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Listado y revocación de sesiones del propio usuario. Revocar una sesión de
 * otro usuario responde 404 (no 403) para no revelar su existencia.
 */
@RestController
@RequestMapping("/auth/sessions")
public class SessionController {

    private final SessionManagementService sessionManagementService;

    public SessionController(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<SessionResponse>>> list(ServerWebExchange exchange) {
        return ReactiveCall.currentUser()
                .flatMap(user -> ReactiveCall.async(() -> sessionManagementService.list(
                        user.userId(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange))))
                .map(sessions -> ResponseEntity.ok(sessions.stream().map(SessionResponse::from).toList()));
    }

    @DeleteMapping("/{sessionId}")
    public Mono<ResponseEntity<Void>> revoke(@PathVariable("sessionId") String sessionId, ServerWebExchange exchange) {
        return ReactiveCall.currentUser()
                .flatMap(user -> ReactiveCall.asyncRun(() -> sessionManagementService.revokeSession(
                        user.userId(), parse(sessionId), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange))))
                .thenReturn(ResponseEntity.noContent().build());
    }

    private SessionId parse(String raw) {
        try {
            return SessionId.from(raw);
        } catch (RuntimeException e) {
            throw new NotFoundException();
        }
    }
}