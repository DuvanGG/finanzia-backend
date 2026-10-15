package com.finanzia.application.model;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.Role;

import java.util.Set;

public record LoginResult(AccessToken accessToken,
                          RefreshToken refreshToken,
                          SessionId sessionId,
                          UserId userId,
                          Set<Role> roles) {
}