package com.finanzia.application.service;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.exception.InvalidRefreshTokenException;
import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.model.TokenPair;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.TokenProvider;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.util.Tokens;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.session.Session;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.User;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Rotación de refresh tokens con detección de reuso.
 * <p>
 * Dos peticiones concurrentes que presentan el mismo token "ganan" cada una un
 * par de tokens: la primera consume el token original; la segunda (dentro de la
 * ventana de gracia) recorre la cadena {@code rotatedTo} hacia la cabeza activa
 * y rota ésta. Reuso por fuera de la ventana de gracia revoca la familia.
 */
public class RefreshSessionService {

    private final RefreshTokenStore sessions;
    private final UserRepository users;
    private final TokenProvider tokenProvider;
    private final AuditLog audit;
    private final AuthSettings settings;
    private final Clock clock;

    public RefreshSessionService(RefreshTokenStore sessions, UserRepository users, TokenProvider tokenProvider,
                                 AuditLog audit, AuthSettings settings, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.tokenProvider = tokenProvider;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    public TokenPair refresh(String presentedValue, String device, IpAddress ip, String correlationId) {
        Instant now = clock.now();
        String hash = Tokens.sha256Hex(presentedValue);
        Session node = sessions.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        int hops = 0;
        while (true) {
            if (node.wasRevoked() || !now.isBefore(node.expiresAt())) {
                throw new InvalidRefreshTokenException("Refresh token revocado o expirado");
            }
            if (!node.isConsumed()) {
                RefreshToken newRefresh = tokenProvider.issueRefreshToken(now, settings.refreshTokenTtl());
                Session successor = Session.create(SessionId.random(), node.userId(), node.familyId(),
                        Tokens.sha256Hex(newRefresh.value()), device, ip, now, settings.refreshTokenTtl());
                sessions.save(successor);
                if (sessions.tryConsumeAndAttach(node.tokenHash(), successor.id(), now)) {
                    User user = users.findById(node.userId())
                            .orElseThrow(InvalidRefreshTokenException::new);
                    if (user.isDisabled()) {
                        throw new InvalidRefreshTokenException("Cuenta deshabilitada");
                    }
                    AccessToken access = tokenProvider.issueAccessToken(user.id(), successor.id(),
                            Set.of(user.role()), now, settings.accessTokenTtl(), settings.issuer());
                    return new TokenPair(access, newRefresh);
                }
                node = sessions.findByTokenHash(node.tokenHash())
                        .orElseThrow(InvalidRefreshTokenException::new);
                if (++hops > 32) {
                    throw new InvalidRefreshTokenException("Cadena de rotación demasiado larga");
                }
                continue;
            }
            if (node.wasConsumedWithin(now, settings.refreshGraceWindow())) {
                SessionId nextId = node.rotatedTo();
                if (nextId == null) {
                    throw new InvalidRefreshTokenException("Refresh token sin sucesor");
                }
                node = sessions.findById(nextId)
                        .orElseThrow(InvalidRefreshTokenException::new);
                if (++hops > 32) {
                    throw new InvalidRefreshTokenException("Cadena de rotación demasiado larga");
                }
                continue;
            }
            sessions.revokeFamily(node.familyId(), now);
            audit.record(new AuditRecord(now, AuditEventType.REFRESH_TOKEN_REUSED, AuditOutcome.FAILURE,
                    node.userId(), ip, device, correlationId,
                    Map.of("sessionId", node.id().toString(), "familyId", node.familyId().toString()),
                    "Reuso de refresh token detectado; familia revocada"));
            throw new InvalidRefreshTokenException("Refresh token reutilizado");
        }
    }
}