package com.finanzia.application.testutil;

import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.application.port.out.EmailSender;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.TokenProvider;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.SessionId;
import com.finanzia.domain.common.TokenFamilyId;
import com.finanzia.domain.common.TokenTtl;
import com.finanzia.domain.common.UserId;
import com.finanzia.domain.session.Session;
import com.finanzia.domain.token.AccessToken;
import com.finanzia.domain.token.RefreshToken;
import com.finanzia.domain.user.CodePurpose;
import com.finanzia.domain.user.EmailCode;
import com.finanzia.domain.user.Role;
import com.finanzia.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementaciones en memoria de los puertos out utilizadas por los tests de
 * la capa de aplicación. Comportamiento fiel a contratos; sin Spring ni I/O.
 */
public final class TestPorts {

    private TestPorts() {
    }

    public static final class FakeClock implements Clock {
        private Instant now;

        public FakeClock(Instant now) {
            this.now = now;
        }

        public void set(Instant now) {
            this.now = now;
        }

        public void advance(Duration d) {
            this.now = now.plus(d);
        }

        @Override
        public Instant now() {
            return now;
        }
    }

    public static final class FakeUserRepository implements UserRepository {
        private final Map<UserId, User> byId = new HashMap<>();
        private final Map<Email, User> byEmail = new HashMap<>();

        @Override
        public synchronized Optional<User> findById(UserId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized Optional<User> findByEmail(Email email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public synchronized User save(User user) {
            byId.put(user.id(), user);
            byEmail.put(user.email(), user);
            return user;
        }

        public synchronized List<User> findAll() {
            return List.copyOf(byId.values());
        }
    }

    public static final class FakePasswordHasher implements PasswordHasher {
        @Override
        public String hash(String plainPassword) {
            return "H{" + plainPassword + "}";
        }

        @Override
        public boolean matches(String plainPassword, String encodedHash) {
            return encodedHash.equals("H{" + plainPassword + "}");
        }
    }

    public static final class FakeTokenProvider implements TokenProvider {
        private final AtomicLong refreshSeq = new AtomicLong();
        private final AtomicLong accessSeq = new AtomicLong();

        @Override
        public AccessToken issueAccessToken(UserId userId, SessionId sessionId, Set<Role> roles,
                                            Instant now, TokenTtl ttl, String issuer) {
            return AccessToken.of("AT-" + userId + "-" + accessSeq.incrementAndGet(),
                    now.plus(ttl.value()));
        }

        @Override
        public RefreshToken issueRefreshToken(Instant now, TokenTtl ttl) {
            return RefreshToken.of("RT-" + refreshSeq.incrementAndGet(), now.plus(ttl.value()));
        }
    }

    public static final class FakeRefreshTokenStore implements RefreshTokenStore {
        private final Map<SessionId, Session> byId = new ConcurrentHashMap<>();
        private final Map<String, Session> byHash = new ConcurrentHashMap<>();

        @Override
        public synchronized void save(Session session) {
            byId.put(session.id(), session);
            byHash.put(session.tokenHash(), session);
        }

        @Override
        public synchronized Optional<Session> findById(SessionId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized Optional<Session> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public synchronized boolean tryConsumeAndAttach(String tokenHash, SessionId successorId, Instant now) {
            Session successor = byId.get(successorId);
            if (successor == null) {
                return false;
            }
            Session s = byHash.get(tokenHash);
            if (s == null || s.wasRevoked() || s.isConsumed() || !now.isBefore(s.expiresAt())) {
                deleteOrphan(successor);
                return false;
            }
            s.consume(now);
            s.attachSuccessor(successorId);
            return true;
        }

        private void deleteOrphan(Session successor) {
            byId.remove(successor.id());
            byHash.remove(successor.tokenHash());
        }

        @Override
        public synchronized void revoke(SessionId id, Instant now) {
            Session s = byId.get(id);
            if (s != null) {
                s.revoke(now);
            }
        }

        @Override
        public synchronized void revokeFamily(TokenFamilyId familyId, Instant now) {
            byId.values().stream()
                    .filter(s -> s.familyId().equals(familyId))
                    .forEach(s -> s.revoke(now));
        }

        @Override
        public synchronized void revokeAllByUser(UserId userId, Instant now) {
            byId.values().stream()
                    .filter(s -> s.userId().equals(userId))
                    .forEach(s -> s.revoke(now));
        }

        @Override
        public synchronized void revokeSessionsExcept(UserId userId, SessionId keep, Instant now) {
            byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && !s.id().equals(keep))
                    .forEach(s -> s.revoke(now));
        }

        @Override
        public synchronized List<Session> listActiveByUser(UserId userId, Instant now) {
            return byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.isActiveAt(now))
                    .toList();
        }

        @Override
        public synchronized int countActiveByUser(UserId userId, Instant now) {
            return (int) byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.isActiveAt(now))
                    .count();
        }

        @Override
        public synchronized Optional<Session> findOldestActiveByUser(UserId userId, Instant now) {
            return byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.isActiveAt(now))
                    .min(Comparator.comparing(Session::createdAt));
        }

        @Override
        public synchronized Optional<Session> findActiveByTokenHash(String tokenHash, Instant now) {
            Session s = byHash.get(tokenHash);
            return (s != null && s.isActiveAt(now)) ? Optional.of(s) : Optional.empty();
        }

        @Override
        public synchronized int purgeForUser(UserId userId, Instant now) {
            List<SessionId> dead = byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && !s.isActiveAt(now))
                    .map(Session::id)
                    .toList();
            dead.forEach(id -> {
                Session removed = byId.remove(id);
                if (removed != null) {
                    byHash.remove(removed.tokenHash());
                }
            });
            return dead.size();
        }

        @Override
        public List<Session> listExpiredOrRevoked() {
            return List.of();
        }

        @Override
        public synchronized boolean deleteRow(SessionId id) {
            Session removed = byId.remove(id);
            if (removed != null) {
                byHash.remove(removed.tokenHash());
            }
            return removed != null;
        }
    }

    public static final class FakeEmailCodeStore implements EmailCodeStore {
        private final List<EmailCode> all = new ArrayList<>();

        @Override
        public synchronized void save(EmailCode code) {
            all.add(code);
        }

        @Override
        public synchronized Optional<EmailCode> findByCodeHashAndPurpose(String codeHash, CodePurpose purpose) {
            return all.stream()
                    .filter(c -> c.codeHash().equals(codeHash) && c.purpose() == purpose)
                    .findFirst();
        }

        @Override
        public synchronized Optional<EmailCode> findLatestByUserAndPurpose(UserId userId, CodePurpose purpose) {
            return all.stream()
                    .filter(c -> c.userId().equals(userId) && c.purpose() == purpose)
                    .max(Comparator.comparing(EmailCode::createdAt));
        }

        @Override
        public synchronized void markConsumed(java.util.UUID id, Instant at) {
            all.stream().filter(c -> c.id().equals(id)).forEach(c -> c.consume(at));
        }

        @Override
        public synchronized void invalidateByUserAndPurpose(UserId userId, CodePurpose purpose, Instant at) {
            all.stream()
                    .filter(c -> c.userId().equals(userId) && c.purpose() == purpose)
                    .forEach(c -> c.consume(at));
        }
    }

    public static final class FakeEmailSender implements EmailSender {
        public record Sent(Email to, String subject, String body) {
        }

        private final List<Sent> sent = new ArrayList<>();

        @Override
        public synchronized void send(Email to, String subject, String body) {
            sent.add(new Sent(to, subject, body));
        }

        public synchronized List<Sent> all() {
            return List.copyOf(sent);
        }
    }

    public static final class FakeLoginAttemptStore implements LoginAttemptStore {
        private final Clock clock;
        private final Map<UserId, Instant> accountLockedUntil = new HashMap<>();
        private final Map<String, Instant> ipBlockedUntil = new HashMap<>();
        private final Map<UserId, Long> accountFailures = new HashMap<>();
        private final Map<String, Long> ipFailures = new HashMap<>();
        private final Map<String, Integer> rateCounters = new HashMap<>();

        public FakeLoginAttemptStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized boolean isAccountLocked(UserId userId) {
            Instant until = accountLockedUntil.get(userId);
            return until != null && clock.now().isBefore(until);
        }

        @Override
        public synchronized boolean isIpBlocked(IpAddress ip) {
            Instant until = ipBlockedUntil.get(ip.value());
            return until != null && clock.now().isBefore(until);
        }

        @Override
        public synchronized void incrementFailures(UserId userId, IpAddress ip, Duration window) {
            accountFailures.merge(userId, 1L, Long::sum);
            ipFailures.merge(ip.value(), 1L, Long::sum);
        }

        @Override
        public synchronized void incrementIpFailures(IpAddress ip, Duration window) {
            ipFailures.merge(ip.value(), 1L, Long::sum);
        }

        @Override
        public synchronized long failureCount(UserId userId) {
            return accountFailures.getOrDefault(userId, 0L);
        }

        @Override
        public synchronized long ipFailureCount(IpAddress ip) {
            return ipFailures.getOrDefault(ip.value(), 0L);
        }

        @Override
        public synchronized void lockAccount(UserId userId, Duration duration) {
            accountLockedUntil.put(userId, clock.now().plus(duration));
        }

        @Override
        public synchronized void blockIp(IpAddress ip, Duration duration) {
            ipBlockedUntil.put(ip.value(), clock.now().plus(duration));
        }

        @Override
        public synchronized Optional<Duration> accountLockRemaining(UserId userId) {
            Instant until = accountLockedUntil.get(userId);
            if (until == null) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(clock.now(), until));
        }

        @Override
        public synchronized Optional<Duration> ipBlockRemaining(IpAddress ip) {
            Instant until = ipBlockedUntil.get(ip.value());
            if (until == null) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(clock.now(), until));
        }

        @Override
        public synchronized void clear(UserId userId, IpAddress ip) {
            accountFailures.remove(userId);
            accountLockedUntil.remove(userId);
            ipFailures.remove(ip.value());
        }

        @Override
        public synchronized boolean isWithinLimit(IpAddress ip, String action, int limit, Duration window) {
            String key = ip.value() + ":" + action;
            int c = rateCounters.getOrDefault(key, 0) + 1;
            rateCounters.put(key, c);
            return c <= limit;
        }

        public synchronized long accountFailureCount(UserId userId) {
            return failureCount(userId);
        }
    }

    public static final class RecordingAuditLog implements AuditLog {
        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public synchronized void record(AuditRecord record) {
            records.add(record);
        }

        public synchronized List<AuditRecord> all() {
            return List.copyOf(records);
        }
    }
}