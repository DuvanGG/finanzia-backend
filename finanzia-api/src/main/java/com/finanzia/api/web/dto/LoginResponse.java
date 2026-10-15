package com.finanzia.api.web.dto;

import com.finanzia.application.model.LoginResult;
import com.finanzia.domain.user.Role;

import java.time.Instant;
import java.util.Set;

public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String sessionId,
        String userId,
        Set<Role> roles) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken().value(), result.accessToken().expiresAt(),
                result.refreshToken().value(), result.refreshToken().expiresAt(),
                result.sessionId().toString(), result.userId().toString(), result.roles());
    }
}