package com.finanzia.api.web;

import com.finanzia.api.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * Utilidades para envolver casos de uso síncronos en el mundo reactivo y
 * obtener el usuario autenticado del contexto de seguridad.
 */
public final class ReactiveCall {

    private ReactiveCall() {
    }

    public static <T> Mono<T> async(Supplier<T> supplier) {
        return Mono.fromCallable(supplier::get).subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Void> asyncRun(Runnable runnable) {
        return Mono.<Void>fromRunnable(runnable).subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<AuthenticatedUser> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class);
    }
}