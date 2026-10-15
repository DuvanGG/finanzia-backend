package com.finanzia.domain.token;

import com.finanzia.domain.common.JwtId;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Access token ya decodificado con los claims relevantes.
 */
public record DecodedAccessToken(JwtId jwtId,
                                 UserId userId,
                                 SessionId sessionId,
                                 Set<Role> roles,
                                 Instant issuedAt,
                                 Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }
}