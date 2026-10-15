## Purpose

Manages the access/refresh token lifecycle: issuance, rotation with reuse detection, per-user session limits, and revocation on logout, credential changes, and account deactivation.

## ADDED Requirements

### Requirement: Access tokens are short-lived
The system SHALL issue access tokens valid for 15 minutes and SHALL reject any access token past its expiry.

#### Scenario: Expired access token is rejected
- **WHEN** a protected request presents an access token older than 15 minutes
- **THEN** the request is rejected as unauthenticated

#### Scenario: Valid access token is accepted
- **WHEN** a protected request presents an access token within its validity window
- **THEN** the request is processed as authenticated

### Requirement: Refresh tokens are long-lived session credentials
The system SHALL issue refresh tokens valid for 7 days, each bound to one session and one user, and SHALL store the token only as an irreversible SHA-256 hash, never as plaintext.

#### Scenario: Plaintext refresh token is never stored
- **WHEN** a refresh token is issued
- **THEN** only its SHA-256 hash is persisted, never the token value itself

#### Scenario: Refresh token is bound to its session and user
- **WHEN** a refresh token is presented
- **THEN** it resolves to exactly one session and one user and is rejected if either does not match

### Requirement: Refresh token rotation
Each successful refresh SHALL return a new access token and a new refresh token, SHALL consume the presented refresh token so it cannot be presented again, and SHALL keep the new token in the same family as the presented one.

#### Scenario: Refresh issues a new token pair
- **WHEN** a valid refresh token is exchanged
- **THEN** a new access token and a new refresh token are returned and the presented token is marked consumed

#### Scenario: Consumed token cannot be refreshed again
- **WHEN** an already-consumed refresh token is presented alone
- **THEN** no new tokens are issued for that token

### Requirement: Refresh token reuse detection
When a refresh token that was already consumed is presented again, the system SHALL revoke every session in its token family, SHALL require the user to log in again, and SHALL record a security event.

#### Scenario: Replay of a consumed token revokes the family
- **WHEN** a previously consumed refresh token is presented a second time
- **THEN** all sessions sharing its family are revoked, the user must re-authenticate, and a security event is recorded

### Requirement: Concurrent refresh does not disrupt active clients
The system SHALL tolerate two near-simultaneous refresh requests that present the same current token by keeping a short grace window, and SHALL NOT revoke the family for submissions within that window.

#### Scenario: Parallel refreshes both succeed
- **WHEN** two requests exchange the same current refresh token within the grace window
- **THEN** both succeed with valid token pairs and no family revocation occurs

### Requirement: Sessions are limited per user
The system SHALL allow at most 10 active sessions per user and SHALL, when a new session exceeds the limit, revoke the oldest session so the newest succeeds.

#### Scenario: Session cap is enforced
- **WHEN** a user already has 10 active sessions and logs in again
- **THEN** the oldest session is revoked and the new session is created

### Requirement: Logout revokes the active session
The system SHALL revoke the session bound to the presented refresh token on logout, so that token can no longer refresh.

#### Scenario: Logout invalidates the session
- **WHEN** a user logs out presenting a valid refresh token
- **THEN** that session is revoked and its refresh token is rejected on any future use

### Requirement: Logout from all sessions
The user SHALL be able to revoke every active session at once.

#### Scenario: Logout-all revokes every session
- **WHEN** a user requests logout from all sessions
- **THEN** every active session for that user is revoked and all their refresh tokens are rejected

### Requirement: Users can list and revoke individual sessions
The system SHALL expose the active sessions of the authenticated user with device, IP, creation and expiry information, and SHALL allow revoking a specific session by its identifier.

#### Scenario: Sessions are listed
- **WHEN** a user lists their sessions
- **THEN** each active session is shown with the authenticated user's device and timing details

#### Scenario: A specific session is revoked
- **WHEN** a user revokes one session by identifier
- **THEN** that session is invalidated while the others remain active

### Requirement: Credential changes revoke sessions
The system SHALL revoke all sessions of the user when the password changes, except the session performing the change when the change is authenticated.

#### Scenario: Password change keeps only the current session
- **WHEN** an authenticated user changes their password
- **THEN** all other sessions are revoked and only the current session remains active

#### Scenario: Password reset revokes everything
- **WHEN** a user resets their password through the recovery flow
- **THEN** all sessions are revoked and every refresh token is rejected

### Requirement: Account deactivation revokes everything
The system SHALL immediately revoke all sessions and block future login when an account is disabled or deleted.

#### Scenario: Disabled account loses all sessions
- **WHEN** an account is disabled
- **THEN** all its sessions are revoked and subsequent refresh and login attempts are rejected