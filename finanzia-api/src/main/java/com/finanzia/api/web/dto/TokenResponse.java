package com.finanzia.api.web.dto;

import com.finanzia.application.model.TokenPair;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken().value(), pair.accessToken().expiresAt(),
                pair.refreshToken().value(), pair.refreshToken().expiresAt());
    }
}