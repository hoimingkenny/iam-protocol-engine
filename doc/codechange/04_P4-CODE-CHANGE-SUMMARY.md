# Phase 4 Code Change Summary

**Phase:** Token Lifecycle (Refresh Rotation, Introspection, Revocation)
**Commits:** `cb4ed8b` (Task 13), `68f43fb` (Task 14)
**Branch:** `phase-4`

---

## Task 13: Refresh Token Rotation with Family Binding

### What Changed

Refresh token rotation (RFC 6749 §6) prevents replay attacks: when a client refreshes, the old refresh token is atomically invalidated and a new one is issued. If a stolen refresh token is used after rotation, the attack fails.

**New `family_id` field on Token** groups an access token and refresh token issued together. When a refresh token is rotated, all tokens in the same family are revoked together.

### Files Changed

#### `auth-core/src/main/java/com/iam/authcore/entity/Token.java`
- Added `familyId` column (`VARCHAR(128)`, nullable)
- Javadoc explains: groups access + refresh token pairs; all family tokens revoked on rotation

#### `auth-core/src/main/java/com/iam/authcore/repository/TokenRepository.java`
- Added `findByFamilyIdAndRevokedFalse(String familyId)` — finds active tokens in a family
- Added `revokeAllByFamilyId(String familyId)` — atomic bulk revocation of entire token family
- `@Modifying(clearAutomatically = true)` on the bulk update query

#### `oauth-oidc/src/main/java/com/iam/oauth/service/TokenService.java`
- `handleAuthorizationCodeGrant`: new `familyId` generated per auth code exchange; passed to both `createAccessToken()` and `createRefreshToken()`
- `handleRefreshTokenGrant`: now calls `tokenRepo.revokeAllByFamilyId(familyId)` instead of single-token revocation. If a stolen refresh token is used after legitimate rotation, both the abused token AND the newly-issued tokens from that exchange are revoked (blast radius containment)
- Pre-Phase-4 tokens (no `familyId`) fall back to single-token revocation for backward compatibility
- `createToken()` helper updated to accept `familyId` parameter

#### `backend/auth-core/src/main/resources/db/migration/V2__add_token_family.sql`
- Adds `family_id VARCHAR(128)` column to `token` table
- Adds partial index `idx_token_family_id` WHERE `family_id IS NOT NULL`

#### `oauth-oidc/src/test/java/com/iam/oauth/service/TokenServiceTest.java`
- `refreshRotation_issuesNewTokensAndRevokesOld_test`: verifies `revokeAllByFamilyId("test-family-1")` is called
- `reuseDetected_revokesFamily_test`: verifies family revocation (not single-token save) when reuse is detected

---

## Task 14: Token Introspection + Revocation

### What Changed

Two new RFC-compliant endpoints added to `oauth-oidc`:

- **`POST /oauth2/introspect`** (RFC 7662) — lets authorized clients query whether a token is still active and get its metadata
- **`POST /oauth2/revoke`** (RFC 7009) — lets clients immediately invalidate a token

### Files Added

#### `oauth-oidc/src/main/java/com/iam/oauth/controller/IntrospectionController.java` (new)
- `POST /oauth2/introspect` — RFC 7662 token introspection
- `Content-Type: application/x-www-form-urlencoded`
- `token` (required): the token value to introspect
- `token_type_hint` (optional): `access_token` or `refresh_token`
- **Active token response** (200):
  ```json
  {
    "active": true,
    "sub": "user-001",
    "client_id": "test-client",
    "scope": "openid profile email",
    "token_type": "Bearer",
    "exp": 1775662439,
    "iat": 1775658839
  }
  ```
- **Inactive token response** (200):
  ```json
  { "active": false }
  ```
- Looks up token by JTI via `tokenRepo.findByJtiAndRevokedFalse(token)`
- Checks `expiresAt > now` and `revoked == false` for liveness
- Active tokens return full metadata; inactive/expired/unknown tokens return `active: false` only

#### `oauth-oidc/src/main/java/com/iam/oauth/controller/RevocationController.java` (new)
- `POST /oauth2/revoke` — RFC 7009 token revocation
- `Content-Type: application/x-www-form-urlencoded`
- `token` (required): the token to revoke
- `token_type_hint` (optional): hint about token type
- **Always returns 200 OK** — per RFC 7009 §2.1, the server must not indicate whether the token existed or was already revoked (prevents token enumeration attacks)
- If token is a refresh token with a `familyId`, also calls `tokenRepo.revokeAllByFamilyId()` to revoke the paired access token — logout security
- `@Transactional` on the controller method to support `@Modifying` repository queries
- `revokeToken()` method: looks up by JTI, sets `revoked = true`, saves

---

## Summary of Changes by Module

| Module | File | Change |
|--------|------|--------|
| `auth-core` | `Token.java` | +`familyId` field |
| `auth-core` | `TokenRepository.java` | +`findByFamilyIdAndRevokedFalse`, `revokeAllByFamilyId` |
| `auth-core` | `V2__add_token_family.sql` | New migration |
| `oauth-oidc` | `TokenService.java` | Family-based rotation in `handleRefreshTokenGrant` and `handleAuthorizationCodeGrant` |
| `oauth-oidc` | `IntrospectionController.java` | New — RFC 7662 `/oauth2/introspect` |
| `oauth-oidc` | `RevocationController.java` | New — RFC 7009 `/oauth2/revoke` |
| `oauth-oidc` | `TokenServiceTest.java` | Tests for family revocation |
