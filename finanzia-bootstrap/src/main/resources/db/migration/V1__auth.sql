-- V1__auth.sql
-- Esquema inicial de autenticación: usuarios, sesiones (refresh tokens) y códigos por email.

CREATE TABLE users (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(320) NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id    UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    device       VARCHAR(255) NULL,
    ip           VARCHAR(64)  NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    rotated_to   UUID         NULL REFERENCES sessions(id) ON DELETE SET NULL,
    revoked_at   TIMESTAMPTZ  NULL,
    consumed_at  TIMESTAMPTZ  NULL
);

CREATE INDEX idx_sessions_user_id   ON sessions (user_id);
CREATE INDEX idx_sessions_family_id ON sessions (family_id);
CREATE INDEX idx_sessions_active    ON sessions (user_id, revoked_at, expires_at);

CREATE TABLE email_codes (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash    VARCHAR(64)    NOT NULL,
    purpose      VARCHAR(20) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_codes_lookup ON email_codes (purpose, code_hash);
CREATE INDEX idx_email_codes_user   ON email_codes (user_id, purpose);