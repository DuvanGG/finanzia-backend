## Purpose

Authenticates users with email and password, issues the session's access and refresh tokens, and protects the login flow against credential stuffing, enumeration, and brute force.

## ADDED Requirements

### Requirement: User logs in with valid credentials
The system SHALL accept a valid email and password for a verified account and SHALL return an access token (15 minute TTL) and a refresh token (7 day TTL) bound to a new session.

#### Scenario: Successful login returns a token pair
- **WHEN** a verified user submits correct credentials
- **THEN** the system returns a valid access token and refresh token and creates a session

#### Scenario: Wrong password is rejected generically
- **WHEN** a verified user submits an incorrect password
- **THEN** login fails with a generic invalid-credentials response and no tokens are issued

#### Scenario: Unknown email is rejected identically
- **WHEN** a user submits an email with no registered account
- **THEN** login fails with the same generic invalid-credentials response as a wrong password

### Requirement: Login does not reveal whether an account exists
The system SHALL perform a password-hash verification step even when the email is unknown, so that response content and timing do not distinguish an unknown email from a wrong password.

#### Scenario: Unknown email still runs hash verification
- **WHEN** the email is unknown
- **THEN** the system still performs an equivalent verification work before returning the generic failure

### Requirement: Unverified accounts cannot log in
The system SHALL reject login for accounts whose email is not verified, regardless of credential correctness, and SHALL direct the user to verify their email without disclosing additional account state.

#### Scenario: Correct credentials on unverified account
- **WHEN** a user with an unverified email submits correct credentials
- **THEN** login is rejected with a verify-email response and no tokens are issued

### Requirement: Passwords are stored irreversibly
The system SHALL store passwords as an irreversible, salted, parameterized hash with a work factor of at least 12, and SHALL never store or log plaintext passwords or raw hashes in a recoverable form.

#### Scenario: Stored credential is a non-recoverable hash
- **WHEN** a password is stored for an account
- **THEN** the stored value is a salted parameterized hash that cannot be reversed to the plaintext

### Requirement: Failed logins are throttled and lock the account
The system SHALL count consecutive failed login attempts per account and per source IP, SHALL apply escalating lockouts once thresholds are crossed, SHALL reject attempts against a locked account even with valid credentials until the lockout expires, and SHALL reset the failure counter on a successful login.

#### Scenario: Repeated failures lock the account
- **WHEN** five consecutive login failures occur for an account
- **THEN** the account is temporarily locked and further login attempts, even with the correct password, are rejected until the lockout expires

#### Scenario: Locked account blocks a valid login
- **WHEN** a login attempt arrives for an account currently locked
- **THEN** the attempt is rejected regardless of password correctness

#### Scenario: Successful login resets the counter
- **WHEN** a user logs in successfully after previous failures
- **THEN** the consecutive-failure counter for that account is reset

#### Scenario: Lockout expires and login resumes
- **WHEN** the lockout period has elapsed
- **THEN** login attempts are evaluated normally again