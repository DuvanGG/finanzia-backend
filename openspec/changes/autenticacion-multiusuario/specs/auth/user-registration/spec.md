## Purpose

Lets people create individual accounts with a verified email and a password that meets the registration policy, in a way that resists account enumeration and abuse.

## ADDED Requirements

### Requirement: Email is normalized before registration
The system SHALL normalize the email (trim whitespace, lowercase the domain and local part, and convert internationalized domains) before storing or comparing, so the same mailbox cannot be registered twice under different spellings.

#### Scenario: Mixed-case duplicate registration is rejected
- **WHEN** a user registers `User@Example.com` and another user registers `user@example.com.`
- **THEN** the second registration is rejected as a duplicate of an existing account

#### Scenario: Normalized email is stored
- **WHEN** a user registers with `  USER@EXAMPLE.COM  `
- **THEN** the account is stored with email `user@example.com`

### Requirement: Password meets the registration policy
The system SHALL reject any password shorter than 12 characters at registration time and SHALL reject passwords that consist only of repeated characters.

#### Scenario: Short password is rejected
- **WHEN** a user submits a password of 11 characters
- **THEN** registration fails with a password-policy error and no account is created

#### Scenario: Weak repeated-character password is rejected
- **WHEN** a user submits the password `aaaaaaaaaaaa`
- **THEN** registration fails with a password-policy error

#### Scenario: Compliant password is accepted
- **WHEN** a user submits a password of 12 or more characters that is not a repetition
- **THEN** registration proceeds to create the account

### Requirement: A registration creates an unverified account
The system SHALL create the account in an unverified state, SHALL send a short-lived, single-use verification code to the registered email, and SHALL NOT allow login until the email is verified.

#### Scenario: Successful registration sends a verification code
- **WHEN** a user registers with a valid, unique email and a compliant password
- **THEN** an unverified account is created and a verification code is sent to the email

#### Scenario: Unverified account cannot log in
- **WHEN** a user with an unverified email attempts to log in with correct credentials
- **THEN** login is rejected and no tokens are issued

### Requirement: Email verification completes registration
The system SHALL accept a valid verification code to mark the account verified, SHALL reject expired or already-used codes, and SHALL allow requesting a new code.

#### Scenario: Valid code verifies the account
- **WHEN** a user submits a valid, unexpired verification code
- **THEN** the account becomes verified and can log in

#### Scenario: Expired code is rejected
- **WHEN** a user submits a verification code past its expiry
- **THEN** verification fails and the user can request a new code

#### Scenario: Reused code is rejected
- **WHEN** a user submits a verification code that was already consumed
- **THEN** verification fails and no account state is changed

### Requirement: Registration resists account enumeration and abuse
The system SHALL respond with a generic, non-revealing message when registering with an email that already exists, SHALL NOT reveal whether an account exists, and SHALL throttle registration attempts per source IP.

#### Scenario: Duplicate registration returns a generic response
- **WHEN** a user registers with an email that is already registered
- **THEN** the response is generic (does not state the email is in use) and no new account is created

#### Scenario: Registration burst is throttled per IP
- **WHEN** more than the allowed number of registration requests arrive from one IP in a short window
- **THEN** further registration attempts from that IP are temporarily rejected