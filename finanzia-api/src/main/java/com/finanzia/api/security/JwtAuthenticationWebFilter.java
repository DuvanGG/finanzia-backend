package com.finanzia.api.security;

import com.finanzia.application.port.out.TokenDecoder;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

/**
 * Filtro de autenticación JWT. No se registra como bean para evitar que Spring
 * Boot lo agregue dos veces al chain de WebFilters; la
 * {@code SecurityConfiguration} lo inserta explícitamente.
 */
public class JwtAuthenticationWebFilter extends AuthenticationWebFilter {

    public JwtAuthenticationWebFilter(TokenDecoder decoder) {
        super(new JwtReactiveAuthenticationManager(decoder));
        setServerAuthenticationConverter(new BearerTokenConverter());
    }
}