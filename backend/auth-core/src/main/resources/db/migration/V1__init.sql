-- V1__init.sql
-- IAM Protocol Engine base schema

-- OAuth Client registry
CREATE TABLE oauth_client (
    client_id          VARCHAR(128) PRIMARY KEY,
    client_secret_hash VARCHAR(256) NOT NULL,
    client_name        VARCHAR(256),
    redirect_uris      TEXT NOT NULL,           -- comma-separated
    allowed_scopes     TEXT NOT NULL,           -- comma-separated
    grant_types        TEXT NOT NULL,            -- comma-separated
    is_public          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Authorization codes (short-lived, Redis-backed but persisted for audit)
CREATE TABLE auth_code (
    code               VARCHAR(128) PRIMARY KEY,
    client_id          VARCHAR(128) NOT NULL REFERENCES oauth_client(client_id),
    subject            VARCHAR(256),             -- user identifier
    code_challenge     VARCHAR(128) NOT NULL,
    code_challenge_method VARCHAR(16) NOT NULL DEFAULT 'S256',
    scope              TEXT,
    nonce              VARCHAR(256),
    expires_at         TIMESTAMPTZ NOT NULL,
    consumed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tokens (access, refresh, ID)
CREATE TABLE token (
    jti                VARCHAR(128) PRIMARY KEY,
    type               VARCHAR(32) NOT NULL,    -- access_token, refresh_token, id_token
    client_id          VARCHAR(128) NOT NULL REFERENCES oauth_client(client_id),
    subject            VARCHAR(256),              -- user identifier (null for client_credentials)
    scope              TEXT,
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked            BOOLEAN NOT NULL DEFAULT FALSE,
    issued_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SCIM Users
CREATE TABLE scim_user (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name          VARCHAR(256) NOT NULL UNIQUE,
    emails             TEXT NOT NULL,             -- comma-separated
    display_name       VARCHAR(512),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    groups             TEXT,                      -- comma-separated group IDs
    attributes         JSONB,
    external_id        VARCHAR(256),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SCIM Groups
CREATE TABLE scim_group (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name       VARCHAR(512) NOT NULL,
    members            TEXT,                      -- comma-separated scim_user.id values
    attributes         JSONB,
    external_id        VARCHAR(256),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- WebAuthn Credentials
CREATE TABLE webauthn_credential (
    credential_id      TEXT PRIMARY KEY,          -- base64url encoded
    user_id            VARCHAR(256) NOT NULL,
    public_key_cose    BYTEA NOT NULL,
    sign_count         BIGINT NOT NULL DEFAULT 0,
    aaguid             UUID NOT NULL,
    attestation_format VARCHAR(64),
    device_type        VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TOTP Credentials
CREATE TABLE totp_credential (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            VARCHAR(256) NOT NULL UNIQUE,
    secret_encrypted   BYTEA NOT NULL,
    verified           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Device Authorization Grant codes (RFC 8628)
CREATE TABLE device_code (
    device_code         VARCHAR(128) PRIMARY KEY,
    user_code           VARCHAR(16) NOT NULL UNIQUE,
    client_id           VARCHAR(128) NOT NULL REFERENCES oauth_client(client_id),
    scope               TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'pending', -- pending, approved, denied, expired
    expires_at          TIMESTAMPTZ NOT NULL,
    approved_by         VARCHAR(256),
    polling_count       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Directory links (LDAP / Entra ID hybrid identity)
CREATE TABLE directory_link (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             VARCHAR(256) NOT NULL,
    directory_source     VARCHAR(64) NOT NULL,    -- ldap, entra_id, etc.
    directory_dn         VARCHAR(1024),
    directory_groups     TEXT,                    -- comma-separated DNs
    synced_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit Event log
CREATE TABLE audit_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type          VARCHAR(64) NOT NULL,
    actor               VARCHAR(256),             -- user who triggered event
    subject             VARCHAR(256),             -- resource affected
    client_id           VARCHAR(128),
    scope               TEXT,
    jti                 VARCHAR(128),
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    details             JSONB,
    timestamp           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_auth_code_client_id ON auth_code(client_id);
CREATE INDEX idx_auth_code_expires_at ON auth_code(expires_at);
CREATE INDEX idx_token_client_id ON token(client_id);
CREATE INDEX idx_token_subject ON token(subject);
CREATE INDEX idx_token_expires_at ON token(expires_at);
CREATE INDEX idx_token_revoked ON token(revoked);
CREATE INDEX idx_scim_user_user_name ON scim_user(user_name);
CREATE INDEX idx_scim_user_active ON scim_user(active);
CREATE INDEX idx_scim_group_display_name ON scim_group(display_name);
CREATE INDEX idx_webauthn_credential_user_id ON webauthn_credential(user_id);
CREATE INDEX idx_device_code_user_code ON device_code(user_code);
CREATE INDEX idx_device_code_status ON device_code(status);
CREATE INDEX idx_device_code_expires_at ON device_code(expires_at);
CREATE INDEX idx_directory_link_user_id ON directory_link(user_id);
CREATE INDEX idx_audit_event_timestamp ON audit_event(timestamp DESC);
CREATE INDEX idx_audit_event_event_type ON audit_event(event_type);
CREATE INDEX idx_audit_event_actor ON audit_event(actor);
