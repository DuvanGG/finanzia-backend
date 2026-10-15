## Why

FinanzIA is a greenfield multi-tenant SaaS (hexagonal architecture, Java 21, Spring Boot 4 / WebFlux) with **zero authentication today**. Every future capability — transactions, budgets, goals, dashboard cache — depends on a secure, per-user identity layer before any data can be isolated. Without it, the product cannot exist.

## What Changes

- Add a self-hosted authentication system backed by PostgreSQL (no external IdP such as Cognito or Keycloak).
- Establish a pure domain + application layer for auth: entities, value objects, domain events, and outbound ports with **no Spring Security dependency** in `finanzia-domain` or `finanzia-application`.
- Add credential-based **registration** and **login** with password policy, email verification, brute-force protection, and user-enumeration resistance.
- Issue **JWT Access Tokens** (15 min TTL) and **Refresh Tokens** (7 day TTL) for a single-page client (Angular) using the `Authorization: Bearer` header; no cookies or server-side sessions.
- Implement **Refresh Token rotation** with family-based **reuse detection** and full **revocation** (logout, logout-all, password change, account disable).
- Persist sessions in **PostgreSQL** (token stored as SHA-256 hash, never plaintext); use Redis only for ephemeral state (rate limiting, lockout counters).
- Add **RBAC** with `USER` and `ADMIN` roles plus mandatory **userId-based data isolation** (IDOR resistance) for all authenticated resources.
- Provide **security audit events** for authentication flows (DynamoDB adapter).
- Spring Security 6+ WebFlux integration lives only in `finanzia-api`/`finanzia-infrastructure` as an adapter over the domain ports.

## Capabilities

### New Capabilities
- `auth/user-registration`: Registration with email normalization, password policy, email verification, and anti-abuse protections.
- `auth/user-login`: Credential authentication, user-enumeration resistance, brute-force/lockout protection, and token issuance on success.
- `auth/password-recovery`: Requesting a password reset for a verified account and setting a new password, with single-use codes and full session revocation.
- `auth/token-session`: Access/refresh token lifecycle — rotation, reuse detection, session limits, revocation, and session management.
- `auth/authorization`: RBAC (USER/ADMIN), deny-by-default, and per-user data isolation for every authenticated resource.
- `auth/security-audit`: Recording of security-relevant events (login success/failure, lockout, token reuse, revocations) for compliance and detection.

### Modified Capabilities
- None. The project has no existing specs.

## Impact

- **Code** (new modules — all currently skeletons):
  - `finanzia-domain`: `User`, `Session` entities, value objects (`Email`, `Password`, `PasswordHash`, `UserId`, `Role`, `Permission`, `TokenFamilyId`, etc.), domain events.
  - `finanzia-application`: use cases (`RegisterUser`, `Login`, `RefreshSession`, `Logout`, `ChangePassword`, session management) and port interfaces (`UserRepository`, `RefreshTokenStore`, `PasswordHasher`, `TokenProvider`, `Clock`, `EmailSender`, `LoginAttemptStore`, `AuditLog`).
  - `finanzia-api`: `@RestController` auth endpoints (`/auth/*`), WebFlux security filter chain, DTOs, OpenAPI.
  - `finanzia-infrastructure`: JPA adapters (`UserJpaEntity`, `SessionJpaEntity` + mappers), BCrypt/Argon2 adapter, JJWT provider, Redis login-attempt store, DynamoDB audit adapter, SES/SMTP email adapter.
- **Dependencies**: `spring-boot-starter-security` (WebFlux flavor), JWT library (JJWT resolved to version 0.12.x), BCrypt via Spring Security crypto.
- **Database**: Flyway migrations for `users` and `sessions` tables (Flyway currently disabled; will be enabled).
- **Config**: signing key (non-secret lookup via env), token TTLs, lockout policy, password policy; CORS allowlist for the Angular origin.
- **Infrastructure**: Redis container added alongside existing Postgres and LocalStack.