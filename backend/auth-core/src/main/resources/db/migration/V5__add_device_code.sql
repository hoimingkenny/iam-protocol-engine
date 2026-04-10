-- V5__add_device_code.sql
-- RFC 8628 Device Authorization Grant

CREATE TABLE device_code (
    device_code   VARCHAR(128) PRIMARY KEY,
    user_code    VARCHAR(16) NOT NULL UNIQUE,
    client_id    VARCHAR(128) NOT NULL,
    scope        TEXT,
    status       VARCHAR(32) NOT NULL DEFAULT 'pending',
    expires_at   TIMESTAMPTZ NOT NULL,
    approved_by  VARCHAR(256),
    polling_count INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_code_user_code ON device_code(user_code);
CREATE INDEX idx_device_code_status ON device_code(status);
CREATE INDEX idx_device_code_expires_at ON device_code(expires_at);
