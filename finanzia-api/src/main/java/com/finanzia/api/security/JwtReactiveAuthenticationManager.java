package com.finanzia.api.security;

import com.finanzia.application.port.out.TokenDecoder;
import com.finanzia.domain.token.DecodedAccessToken;
import com.finanzia.domain.user.Role;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Valida el access token con {@link TokenDecoder} y construye la autenticación
 * con el {@link AuthenticatedUser} y las authorities {@code ROLE_*}.
 */
class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final TokenDecoder decoder;

    JwtReactiveAuthenticationManager(TokenDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String token) || token.isBlank()) {
            return Mono.error(new BadCredentialsException("Missing bearer token"));
        }
        return Mono.fromCallable(() -> decoder.decode(token))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toAuthentication)
                .onErrorMap(error -> new BadCredentialsException("Invalid or expired access token", error));
    }

    private Authentication toAuthentication(DecodedAccessToken decoded) {
        AuthenticatedUser principal =
                new AuthenticatedUser(decoded.userId(), decoded.sessionId(), decoded.roles());
        List<GrantedAuthority> authorities = decoded.roles().stream()
                .map(Role::name)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, decoded.jwtId().toString(), authorities);
    }
}