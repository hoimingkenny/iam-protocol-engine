# Phase 8 Code Change Summary

**Phase:** Modern Auth (TOTP MFA + WebAuthn + Device Authorization Grant)
**Branch:** `phase-8` (merged to `main`)
**RFCs:** RFC 6238 (TOTP), RFC 8628 (Device Authorization Grant), W3C WebAuthn (FIDO2)

---

## What Was Built

Phase 8 implements three modern authentication mechanisms:

1. **TOTP MFA** (RFC 6238) — Time-based one-time passwords using `dev.samstevens.totp`; secrets stored encrypted with AES-256-GCM
2. **WebAuthn / FIDO2** — Passwordless public key authentication using `com.webauthn4j:webauthn4j-core`
3. **Device Authorization Grant** (RFC 8628) — For CLI tools, smart TVs, and input-constrained devices

---

## Modules Changed

| Module | Change |
|--------|--------|
| `backend/mfa/` | **New module** — TOTP + WebAuthn MFA services and controllers |
| `backend/device-flow/` | **New module** — RFC 8628 device code authorization |
| `backend/auth-core/` | Added `totp_credential`, `webauthn_credential`, `device_code` JPA entities; V3/V4/V5 Flyway migrations |
| `backend/oauth-oidc/` | Added `device_code` grant handling in `TokenController.handleDeviceCodeGrant()` |
| `backend/api-gateway/` | Added `mfa` and `device-flow` module dependencies |

---

## Task 24: TOTP MFA — RFC 6238

### Files Added

#### `backend/mfa/pom.xml` (new)
New Maven module. Depends on `auth-core`. Key dependencies:
- `dev.samstevens.totp:totp:1.7.1` — TOTP generation and verification
- `com.google.zxing:javase:3.5.3` — QR code PNG generation
- `com.webauthn4j:webauthn4j-core:0.31.2.RELEASE` — WebAuthn support

#### `backend/mfa/src/main/java/com/iam/mfa/service/TotpService.java` (new)
Core TOTP logic:
- `generateSetup(userId, issuer)` — generates 160-bit secret, encrypts with AES-256-GCM, stores unverified, returns provisioning URI + QR PNG
- `verify(userId, code)` — decrypts secret, verifies code, marks `verified=true` on first success
- `isEnrolled(userId)` — checks if user has a verified TOTP credential
- AES key: `"iam-demo-totp-enc-key-32bytes000"` (exactly 32 bytes; production would use KMS)
- IV: 12 random bytes per encryption (GCM IV_LENGTH)

#### `backend/mfa/src/main/java/com/iam/mfa/controller/TotpController.java` (new)
Endpoints:
- `POST /mfa/totp/setup` — Bearer token auth; generates secret + QR; requires valid access token via `TokenRepository.findByJtiAndRevokedFalse()`
- `POST /mfa/totp/verify` — validates 6-digit TOTP code
- `GET /mfa/totp/status` — returns `{ enrolled: boolean }`

### Files Modified

#### `backend/auth-core/src/main/java/com/iam/authcore/entity/TotpCredential.java` (new)
JPA entity for `totp_credential` table. Fields: `id` (UUID), `userId` (unique), `secretEncrypted` (BYTEA), `verified` (boolean), `createdAt`, `updatedAt`.

#### `backend/auth-core/src/main/java/com/iam/authcore/repository/TotpCredentialRepository.java` (new)
Repository with `findByUserId()` and `deleteByUserId()`.

#### `backend/auth-core/src/main/resources/db/migration/V3__add_totp_credential.sql` (new)
```sql
CREATE TABLE totp_credential (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(256) NOT NULL UNIQUE,
    secret_encrypted BYTEA NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Task 25: WebAuthn / FIDO2

### Files Added

#### `backend/mfa/src/main/java/com/iam/mfa/service/WebAuthnService.java` (new)
Core WebAuthn logic:
- `beginRegistration(userId)` — creates `PublicKeyCredentialCreationOptions` with challenge, RP identity, user entity
- `completeRegistration(userId, response)` — validates attestation, stores credential with `signCount=0`
- `beginAuthentication(userId)` — creates `PublicKeyCredentialRequestOptions` with challenge
- `completeAuthentication(response)` — verifies assertion signature, validates `signCount` (anti-cloning), increments stored `signCount`
- Challenge: 32+ bytes from `SecureRandom`, stored in-memory per ceremony (never persisted)
- AAGUID stored per credential for authenticator identification

#### `backend/mfa/src/main/java/com/iam/mfa/controller/WebAuthnController.java` (new)
Endpoints:
- `POST /webauthn/register/begin` — returns credential creation options (Bearer auth)
- `POST /webauthn/register/complete` — processes registration response
- `POST /webauthn/authenticate/begin` — returns credential request options
- `POST /webauthn/authenticate/complete` — processes authentication response

### Files Modified

#### `backend/auth-core/src/main/java/com/iam/authcore/entity/WebAuthnCredential.java` (new)
JPA entity for `webauthn_credential` table. Fields: `credentialId` (VARCHAR(512) PK), `userId`, `publicKeyCose` (BYTEA), `signCount` (BIGINT), `aaguid` (UUID), `attestationFormat`, `deviceType`, `createdAt`, `updatedAt`.

#### `backend/auth-core/src/main/java/com/iam/authcore/repository/WebAuthnCredentialRepository.java` (new)
Repository with `findByUserId()`, `findByCredentialId()`.

#### `backend/auth-core/src/main/resources/db/migration/V4__add_webauthn_credential.sql` (new)
```sql
CREATE TABLE webauthn_credential (
    credential_id VARCHAR(512) PRIMARY KEY,
    user_id VARCHAR(256) NOT NULL,
    public_key_cose BYTEA NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    aaguid UUID NOT NULL,
    attestation_format VARCHAR(64),
    device_type VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Task 26: Device Authorization Grant — RFC 8628

### Files Added

#### `backend/device-flow/pom.xml` (new)
New Maven module. Depends on `auth-core`, `oauth-oidc`.

#### `backend/device-flow/src/main/java/com/iam/deviceflow/service/DeviceFlowService.java` (new)
Core RFC 8628 logic:
- `authorize(clientId, scope)` — generates 128-bit `device_code` and 16-char `user_code` (formatted `XXXX-XXXX`); stores with 10-minute TTL; returns `DeviceAuthorizationResult`
- `approve(userCode)` — marks `device_code` status `approved`
- `getApprovalInfo(userCode)` — returns client ID, scope, validity for HTML page
- `PollingStatus` enum: `COMPLETED`, `AUTHORIZATION_PENDING`, `SLOW_DOWN`, `EXPIRED`, `ACCESS_DENIED`, `SERVER_ERROR`

#### `backend/device-flow/src/main/java/com/iam/deviceflow/controller/DeviceAuthorizationController.java` (new)
Endpoints:
- `POST /device_authorization` — returns `device_code`, `user_code`, `verification_uri`, `expires_in`, `interval` (RFC 8628 §3.2)
- `GET /device?user_code=...` — HTML approval page with user code display and approve button
- `POST /device/approve` — user submits approval form

#### `backend/oauth-oidc/src/main/java/com/iam/oauth/controller/TokenController.java` (modified)
Added `device_code` parameter and `handleDeviceCodeGrant()` method:
- Polls device code status: `authorization_pending` (400), `access_denied` (400), `token_expired` (400), approved → issue tokens
- Device code consumed on successful token issuance

### Files Modified

#### `backend/auth-core/src/main/java/com/iam/authcore/entity/DeviceCode.java` (new)
JPA entity for `device_code` table. Fields: `deviceCode` (VARCHAR(128) PK), `userCode` (VARCHAR(16) UNIQUE), `clientId`, `scope`, `status` (pending/approved/denied/expired), `expiresAt`, `approvedBy`, `pollingCount`, `createdAt`.

#### `backend/auth-core/src/main/java/com/iam/authcore/repository/DeviceCodeRepository.java` (new)
Repository with `findById()`, `save()`, `deleteById()`.

#### `backend/auth-core/src/main/resources/db/migration/V5__add_device_code.sql` (new)
```sql
CREATE TABLE device_code (
    device_code VARCHAR(128) PRIMARY KEY,
    user_code VARCHAR(16) NOT NULL UNIQUE,
    client_id VARCHAR(128) NOT NULL,
    scope TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    expires_at TIMESTAMPTZ NOT NULL,
    approved_by VARCHAR(256),
    polling_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Key Implementation Details

### TOTP Secret Storage
AES-256-GCM with random 12-byte IV per encryption. IV prepended to ciphertext. Key is a static 32-byte demo key (production: AWS KMS or HashiCorp Vault).

### WebAuthn Anti-Cloning
`signCount` stored with each credential. On authentication, presented `signCount` must be strictly greater than stored value. After successful auth, stored value is updated.

### Device Code Polling
Minimum polling interval: 5 seconds (`interval` in response). Exceeding interval returns `slow_down` (client should wait twice the interval before next poll). Device code deleted after successful token issuance.

---

## Build & Test

```bash
./mvnw clean install                    # All modules build successfully
./mvnw test                             # All tests pass
./mvnw flyway:migrate -pl backend/auth-core  # Apply V3, V4, V5 migrations
```
