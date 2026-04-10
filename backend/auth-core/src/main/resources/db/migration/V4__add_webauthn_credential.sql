-- V4__add_webauthn_credential.sql
-- WebAuthn credential store per W3C Web Authentication spec

CREATE TABLE webauthn_credential (
    credential_id    VARCHAR(512) PRIMARY KEY,
    user_id         VARCHAR(256) NOT NULL,
    public_key_cose BYTEA NOT NULL,
    sign_count      BIGINT NOT NULL DEFAULT 0,
    aaguid          UUID NOT NULL,
    attestation_format VARCHAR(64),
    device_type     VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webauthn_credential_user_id ON webauthn_credential(user_id);
