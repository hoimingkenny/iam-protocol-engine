-- V3__add_totp_credential.sql
-- TOTP MFA credentials per RFC 6238

CREATE TABLE totp_credential (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           VARCHAR(256) NOT NULL UNIQUE,
    secret_encrypted  BYTEA NOT NULL,
    verified          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_totp_credential_user_id ON totp_credential(user_id);
