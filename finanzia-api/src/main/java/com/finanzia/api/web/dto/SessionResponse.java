package com.finanzia.api.web.dto;

import com.finanzia.application.model.SessionInfo;

import java.time.Instant;

public record SessionResponse(
        String id,
        String device,
        String ip,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt) {

    public static SessionResponse from(SessionInfo info) {
        return new SessionResponse(info.id().toString(), info.device(), info.ip().value(),
                info.createdAt(), info.expiresAt(), info.hadLastUsed());
    }
}