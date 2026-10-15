## Purpose

Lets users reset a forgotten password through a secure recovery flow with single-use, short-lived codes that invalidate all sessions when the new password is set.

## ADDED Requirements

### Requirement: Password reset can be requested for a verified account
The system SHALL accept a reset request for a registered email and SHALL send a short-lived, single-use reset code to that address, and SHALL respond generically so the request does not reveal whether the account exists.

#### Scenario: Reset request sends a code
- **WHEN** a user requests a password reset for a registered, verified email
- **THEN** a single-use reset code with a short expiry is sent to that email

#### Scenario: Reset request for an unknown email is generic
- **WHEN** a user requests a password reset for an unregistered email
- **THEN** the response is identical to a successful request and no code is sent

#### Scenario: Reset request for an unverified account
- **WHEN** a user requests a password reset for an account whose email is not verified
- **THEN** no reset code is sent and the response is identical to an unknown-email request

### Requirement: New password is set with a valid code
The system SHALL accept a new password that meets the password policy together with a valid, unexpired, single-use reset code, SHALL update the password, and SHALL reject expired, used, or incorrect codes.

#### Scenario: Valid code sets the new password
- **WHEN** a user submits a compliant new password with a valid, unexpired, unused reset code
- **THEN** the password is updated and the code is consumed

#### Scenario: Expired or used code is rejected
- **WHEN** a user submits a reset code that is expired or already consumed
- **THEN** the new password is not applied and an invalid-code response is returned

#### Scenario: Weak new password is rejected
- **WHEN** a user submits a new password that violates the password policy
- **THEN** the password is not applied and a password-policy error is returned

### Requirement: Setting a new password revokes all sessions
After a password reset is completed, the system SHALL revoke every session of the user and SHALL require a fresh login.

#### Scenario: Reset invalidates all sessions
- **WHEN** a password reset successfully updates the password
- **THEN** all sessions for that user are revoked and every refresh token is rejected

### Requirement: Recovery attempts are throttled
The system SHALL throttle password-reset requests per email and per source IP to limit abuse and code guessing.

#### Scenario: Excessive reset requests are throttled
- **WHEN** more than the allowed number of reset requests arrive from one source in a short window
- **THEN** further reset requests from that source are temporarily rejected