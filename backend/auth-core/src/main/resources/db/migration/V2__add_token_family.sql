-- V2__add_token_family.sql
-- Token family for refresh rotation security (RFC 6749 §6):
-- When a refresh token is used, all tokens in the same family are invalidated.
-- This prevents "refresh token replay" attacks where a stolen refresh token
-- is used after the legitimate client has already rotated.

ALTER TABLE token
  ADD COLUMN family_id VARCHAR(128);

-- Index for fast family lookups during reuse detection
CREATE INDEX idx_token_family_id ON token(family_id) WHERE family_id IS NOT NULL;

-- Existing tokens (from Phase 1/2) have no family — they are not yet rotated
-- and will be assigned a family_id on their next refresh.
