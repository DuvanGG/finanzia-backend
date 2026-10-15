## Purpose

Enforces deny-by-default authentication, role-based access control with USER and ADMIN roles, and per-user data isolation so users can only see and modify their own resources.

## ADDED Requirements

### Requirement: Protected resources require authentication
The system SHALL require a valid, unexpired access token for every API resource except the public authentication endpoints, and SHALL reject unauthenticated or invalid requests with an unauthenticated response.

#### Scenario: No token is rejected
- **WHEN** a request to a protected resource has no access token
- **THEN** the request is rejected as unauthenticated

#### Scenario: Valid token is accepted
- **WHEN** a request to a protected resource presents a valid, unexpired access token
- **THEN** the request is processed as authenticated

### Requirement: RBAC with USER and ADMIN roles
The system SHALL define the roles `USER` and `ADMIN`, SHALL assign `USER` by default on registration, and SHALL allow `ADMIN` requests to perform administrative operations that `USER` requests cannot.

#### Scenario: Default role is USER
- **WHEN** a new account is created through registration
- **THEN** the account has the `USER` role

#### Scenario: USER is denied administrative operations
- **WHEN** a user with the `USER` role requests an administrative operation
- **THEN** the request is rejected with a forbidden response

#### Scenario: ADMIN can perform administrative operations
- **WHEN** a user with the `ADMIN` role requests an administrative operation
- **THEN** the request is processed

### Requirement: Role changes require a new access token
The system SHALL ensure that access tokens issued before a role change do not grant the new role; the user SHALL obtain a fresh access token to use changed permissions.

#### Scenario: Existing token does not gain new role
- **WHEN** a user's role is escalated to `ADMIN` and they present an access token issued before the change
- **THEN** the request is not authorized for the new administrative operation until a fresh token is obtained

### Requirement: Users can only access their own resources
For every resource identified by an id, the system MUST verify that the resource belongs to the authenticated user, SHALL return a not-found response for any resource belonging to another user, and SHALL make non-existent and other-user resources indistinguishable.

#### Scenario: Access to own resource is allowed
- **WHEN** a user requests one of their own resources
- **THEN** the resource is returned

#### Scenario: Access to another user's resource returns not-found
- **WHEN** a user requests a resource belonging to a different user
- **THEN** the response is not-found and reveals only that the resource does not exist for that user

#### Scenario: Access to a non-existent resource is indistinguishable
- **WHEN** a user requests a resource id that does not exist anywhere
- **THEN** the response is identical to the other-user case

### Requirement: Data access is scoped server-side
The system MUST derive the owner from the authenticated session and MUST always include that owner as a mandatory filter in data access, never relying on client-supplied ownership or ids.

#### Scenario: Client-supplied ownership is ignored
- **WHEN** a request specifies an id or ownership value that differs from the authenticated user
- **THEN** the server still scopes the access to the authenticated user and the other-user resource is not returned