package com.finanzia.api.web;

import com.finanzia.application.exception.NotFoundException;
import com.finanzia.application.service.DisableAccountService;
import com.finanzia.domain.common.UserId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Operaciones administrativas. Solo accesibles con rol ADMIN
 * ({@link PreAuthorize}); un USER recibe 403.
 */
@RestController
@RequestMapping("/admin/users")
public class AdminController {

    private final DisableAccountService disableAccountService;

    public AdminController(DisableAccountService disableAccountService) {
        this.disableAccountService = disableAccountService;
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> disable(@PathVariable("userId") String userId, ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> disableAccountService.disable(
                        parse(userId), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.noContent().build());
    }

    private UserId parse(String raw) {
        try {
            return UserId.from(raw);
        } catch (RuntimeException e) {
            throw new NotFoundException();
        }
    }
}