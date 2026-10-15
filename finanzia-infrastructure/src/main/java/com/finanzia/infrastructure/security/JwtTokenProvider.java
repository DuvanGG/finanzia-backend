package com.finanzia.infrastructure.security;

import com.finanzia.application.port.out.TokenDecoder;
import com.finanzia.application.port.out.TokenProvider;
import com.finanzia.domain.DomainException;
import com.finanzia.domain.common.JwtId;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.DecodedAccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Emisión y validación de JWTs HS256 (outbound ports TokenProvider/TokenDecoder).
 * Claims emitidos: iss, aud, sub=userId, sid, jti, iat, exp, roles.
 * Tolerancia de reloj ±30 s; rechaza tokens con firma inválida o vencidos.
 */
@Component
public class JwtTokenProvider implements TokenProvider, TokenDecoder {

    private static final String AUDIENCE = "finanzia";
    private static final String CLAIM_SESSION = "sid";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final String issuer;
    private final String kid;
    private final JwtParser parser;

    public JwtTokenProvider(@Value("${finanzia.jwt.secret}") String secret,
                            @Value("${finanzia.jwt.issuer:finanzia}") String issuer,
                            @Value("${finanzia.jwt.kid:finanzia-access-v1}") String kid) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalArgumentException("finanzia.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.issuer = issuer;
        this.kid = kid;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(30)
                .requireIssuer(issuer)
                .build();
    }

    @Override
    public AccessToken issueAccessToken(UserId userId, SessionId sessionId, Set<Role> roles,
                                        Instant now, TokenTtl ttl, String issuer) {
        String jti = JwtId.random().toString();
        String value = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
                .id(jti)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(ttl.value())))
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_ROLES, roles.stream().map(Role::name).toList())
                .signWith(key)
                .compact();
        return AccessToken.of(value, now.plus(ttl.value()));
    }

    @Override
    public RefreshToken issueRefreshToken(Instant now, TokenTtl ttl) {
        String value = "rt." + UUID.randomUUID() + "." + UUID.randomUUID();
        return RefreshToken.of(value, now.plus(ttl.value()));
    }

    @Override
    public DecodedAccessToken decode(String accessToken) {
        try {
            Claims claims = parser.parseSignedClaims(accessToken).getPayload();
            List<String> roles = claims.get(CLAIM_ROLES, List.class);
            Role[] parsedRoles = new Role[roles.size()];
            for (int i = 0; i < roles.size(); i++) {
                parsedRoles[i] = Role.valueOf(roles.get(i));
            }
            return new DecodedAccessToken(
                    JwtId.from(claims.getId()),
                    UserId.from(claims.getSubject()),
                    SessionId.from(claims.get(CLAIM_SESSION, String.class)),
                    Set.of(parsedRoles),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant());
        } catch (RuntimeException e) {
            throw new DomainException("Invalid access token", e);
        }
    }
}