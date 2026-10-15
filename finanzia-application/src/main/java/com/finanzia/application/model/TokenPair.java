package com.finanzia.application.model;

import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;

/**
 * Par de tokens devuelto al cliente.
 */
public record TokenPair(AccessToken accessToken, RefreshToken refreshToken) {
}