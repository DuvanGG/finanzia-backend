## Purpose

Records security-relevant events across authentication flows into a durable, append-only log for detection, forensics, and compliance.

## ADDED Requirements

### Requirement: Security events are recorded
The system SHALL record a security event for registration, email verification, login success, login failure, account lockout, refresh-token reuse detection, session revocation (logout, logout-all, and device revocation), password change, password reset, and account disable.

#### Scenario: Login failure records an event
- **WHEN** a login attempt fails
- **THEN** a security event is recorded for that failure

#### Scenario: Token reuse records an event
- **WHEN** refresh-token reuse is detected
- **THEN** a security event is recorded describing the reuse

#### Scenario: Session revocation records an event
- **WHEN** a session is revoked (logout, logout-all, or device revocation)
- **THEN** a security event is recorded for the revocation

### Requirement: Audit records carry actor and context
Each security event SHALL include a timestamp, an event type, an outcome (success or failure), the user identifier when known, the source IP address, the user-agent/device information, and a correlation id.

#### Scenario: Audit record contains all context fields
- **WHEN** a security event is recorded
- **THEN** the record includes timestamp, type, outcome, user id when available, IP, user-agent, and correlation id

### Requirement: Audit records exclude secrets
Audit records MUST NOT contain passwords, refresh tokens, access tokens, or password hashes.

#### Scenario: No secrets in audit records
- **WHEN** any security event is recorded
- **THEN** the record contains no password, token, or hash values

### Requirement: Audit log is append-only and durable
The audit log SHALL be append-only, SHALL NOT allow modification or deletion through application flows, and SHALL retain events such that an accepted event survives subsequent failures.

#### Scenario: Events are persisted durably
- **WHEN** a security event is accepted
- **THEN** it is persisted durably and remains available for later inspection

#### Scenario: Events cannot be altered by application flows
- **WHEN** application requests are made during normal operation
- **THEN** no application flow exposes update or deletion of previously recorded audit events