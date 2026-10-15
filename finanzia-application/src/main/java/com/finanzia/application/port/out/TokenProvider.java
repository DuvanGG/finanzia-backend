package com.finanzia.application.port.out;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Emisión de tokens (outbound port). Emisor/validador de JWTs en infraestructura.
 */
public interface TokenProvider {

    AccessToken issueAccessToken(UserId userId, SessionId sessionId, Set<Role> roles,
                                 Instant now, TokenTtl ttl, String issuer);

    /**
     * Refresh token opaco de alta entropía; nunca se persiste en claro.
     */
    RefreshToken issueRefreshToken(Instant now, TokenTtl ttl);
}