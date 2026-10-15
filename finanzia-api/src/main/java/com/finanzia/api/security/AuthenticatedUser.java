package com.finanzia.api.security;

import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.user.Role;

import java.security.Principal;
import java.util.Set;

/**
 * Principal autenticado derivado del access token: identifica al usuario, la
 * sesión (para operaciones como change-password) y sus roles.
 */
public record AuthenticatedUser(UserId userId, SessionId sessionId, Set<Role> roles) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}