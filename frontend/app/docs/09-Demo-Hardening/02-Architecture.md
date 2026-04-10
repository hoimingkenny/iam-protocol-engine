---
title: Architecture Reference
sidebar_position: 3
description: Complete architecture reference — module map, data model, security decisions, and sequence diagrams.
---

# Architecture Reference

**File:** `README.md`
**Mermaid diagrams:** 5 sequence diagrams covering PKCE, refresh rotation, SCIM JML, device flow, TOTP

---

## Module Map

```
iam-protocol-engine/
├── backend/
│   ├── auth-core/              # JPA entities, AuditService, repositories, Flyway migrations
│   ├── oauth-oidc/             # /authorize, /token, OIDC discovery, JWKS, ID token, /userinfo
│   ├── saml-federation/        # SAML SP: metadata, AuthnRequest, ACS, SAML→OIDC bridge
│   ├── scim/                   # SCIM 2.0 /Users, /Groups, JML lifecycle hooks
│   ├── mfa/                    # TOTP (RFC 6238) + WebAuthn/FIDO2 (W3C)
│   ├── device-flow/            # RFC 8628 Device Authorization Grant
│   ├── demo-resource/          # Protected sample API (validates Bearer tokens)
│   └── api-gateway/            # Spring Boot @SpringBootApplication entry point
├── frontend/
│   └── app/                   # Docusaurus learning site + Admin UI scaffold
├── scripts/
│   └── demo-e2e.sh             # End-to-end demo script
└── infra/
    └── docker-compose.yml       # PostgreSQL 16 + Redis 7
```

**Key constraint:** `auth-core` is the only module with JPA entities. All other modules depend on it for data access.

---

## Data Model

### Entity Summary

| Entity | Primary Key | Notable Design |
|--------|-------------|----------------|
| `OAuthClient` | `client_id` | `client_secret_hash` (SHA-256), `redirect_uris` as comma-separated TEXT |
| `AuthCode` | `code` | `code_challenge` stored verbatim, 5-min TTL, consumed after single use |
| `Token` | `jti` | `type` value among access_token, refresh_token, id_token, `family_id` for rotation groups |
| `ScimUser` | `id` (UUID) | `user_name` UNIQUE, `groups` as comma-separated TEXT, `attributes` JSONB |
| `ScimGroup` | `id` (UUID) | `members` as comma-separated scim_user.id TEXT, `attributes` JSONB |
| `WebAuthnCredential` | `credential_id` (base64url) | `public_key_cose` BYTEA, `sign_count` BIGINT |
| `TotpCredential` | `id` (UUID) | `user_id` UNIQUE, `secret_encrypted` BYTEA (AES-256-GCM) |
| `DeviceCode` | `device_code` | `user_code` UNIQUE, 16-char formatted code, `status` value among pending/approved/denied/expired |
| `AuditEvent` | `id` | JSONB `details` column for structured event data |

### Why Comma-Separated TEXT for Arrays?

PostgreSQL has native JSONB but no native array type. Other projects might use `TEXT[]` or `JSONB`. This project uses comma-separated TEXT for `redirect_uris`, `scopes`, `groups`, and `grant_types` because:
- Simpler schema, no custom type casting needed
- `LIKE` queries work directly (e.g., `WHERE ',' || scopes || ',' LIKE '%,openid,%'`)
- No JSON parsing overhead for simple membership checks
- `attributes` on SCIM entities uses JSONB because SCIM schemas are complex and extensibility is required per RFC 7643

---

## Security Decisions

| Decision | Implementation | Why It Matters |
|----------|---------------|----------------|
| **PKCE required for public clients** | `code_challenge_method=S256` enforced at `/authorize` | Auth code interception on public networks becomes useless without the `code_verifier` |
| **RS256 (asymmetric) for all tokens** | JWS signed with RSA-SHA256, public key in JWKS | Compromised server doesn't expose signing key; only the public key need be shared |
| **`kid` in JWKS from day one** | Each key has a stable `kid` derived from its thumbprint | Key rotation works without breaking old tokens; rolling keys is a config change, not a code change |
| **`redirect_uri` exact match** | String equality check against registered URI | Pattern matching is an injection vector; exact match is unambiguous |
| **Refresh token rotation** | Old token atomically revoked on reuse, same `family_id` | Prevents replay if old token is stolen; "family sweep" revokes entire token lineage |
| **Device code single-use** | Deleted from PostgreSQL after successful token issuance | Device code cannot be replayed after consumption |
| **TOTP secret AES-256-GCM encrypted** | 12-byte random IV per encryption | Plaintext secret never hits the DB; IV reuse impossible across encryptions |
| **WebAuthn `sign_count` anti-cloning** | Server stores and increments counter on each auth | Cloned credential fails `sign_count` check after first legitimate use |
| **Audit log on every token operation** | `AuditService.audit(event)` called from token issuance, revocation, introspection | Incident response requires a paper trail; every token has a `jti` for correlation |
| **PostgreSQL for short-lived state** | Auth codes (5min TTL), device codes (10min TTL), refresh tokens (7d TTL) | Redis is ephemeral; if Redis state is lost, tokens become invalid. PostgreSQL is durable. |

---

## Sequence Diagrams

### OAuth 2.0 Auth Code + PKCE

```
Client            Authorization Server         PostgreSQL
  |                      |                        |
  |-- GET /authorize ---->|                        |
  |   code_challenge=...  |-- Store auth_code ---->|
  |                      |   (5min TTL, unconsumed) |
  |<-- 302 redirect -----|                        |
  |   ?code=AUTHCODE      |                        |
  |                      |                        |
  |-- POST /token -------->|                        |
  |   code + verifier     |-- Verify code_challenge|
  |                      |   = SHA256(verifier)   |
  |                      |-- Mark auth_code used ->|
  |                      |-- Issue tokens -------->|
  |<-- access_token -----|                        |
  |   refresh_token      |                        |
  |   id_token           |                        |
```

### Refresh Token Rotation

```
Client            Auth Server              PostgreSQL
  |-- POST /token (refresh_token=R1) -->|
  |                                    |-- Lookup R1, active=true
  |                                    |-- Issue R2 + AT2
  |                                    |-- Revoke R1 + AT1 atomically
  |<-- access_token_2 + R2 ----------|
  |
  | (Attacker reuses stolen R1)
  |-- POST /token (refresh=R1) -------->|
  |<-- 400 invalid_grant --------------|
  | (Family sweep: AT1 also revoked)  |
```

### SCIM JML Lifecycle

```
SCIM Client      ScimUserService       TokenService       PostgreSQL
    |                 |                    |                  |
    | JOINER          |                    |                  |
    |-- POST /Users ->|                    |                  |
    |                 |-- audit(joiner) -->|                  |
    |                 |-- INSERT user ---->|---------------->|
    |<-- 201 ---------|                    |                  |
    |                 |                    |                  |
    | MOVER           |                    |                  |
    |-- PUT /Users -->|                    |                  |
    |                 |-- UPDATE user ---->|---------------->|
    |<-- 200 ---------|                    |                  |
    |                 |                    |                  |
    | LEAVER          |                    |                  |
    |-- DELETE /User->|                    |                  |
    |                 |-- revokeAllTokens ->|                  |
    |                 |                    |-- UPDATE token -->|
    |                 |                    |   revoked=true   |
    |                 |-- DELETE user ----->|---------------->|
    |<-- 204 ---------|                    |                  |
```

### Device Authorization Grant (RFC 8628)

```
Device (TV)      Auth Server           User (phone)        PostgreSQL
  |-- POST /device_authorization -->|
  |<-- { device_code, user_code }-|
  |                               |------- GET /device?user_code=XXXX ->|
  |<------------------------------ HTML page with approve button --|
  |                               |-- POST /device/approve -------->|
  |                               |-- UPDATE status=approved ----->|
  |<------------------------------ Device approved ----------------|
  |                               |                  |
  | (poll every 5s)               |                  |
  |-- POST /token (device_code) -->|                  |
  |<-- 400 authorization_pending-| (not yet approved)|
  | (wait 5s)                     |                  |
  |-- POST /token (device_code) -->|                  |
  |<-- { access_token, ... } ------| (after approval)|
  |                               |                  |-- DELETE device_code
```

### TOTP Enrollment

```
User             TotpService           Auth App          PostgreSQL
  |-- POST /mfa/totp/setup -->|
  |   (Bearer token)         |-- Generate 160-bit secret
  |                           |-- AES-256-GCM encrypt
  |                           |-- INSERT totp_credential
  |<-- { secret, qrCodeImage }-|   (verified=false)
  |                           |                  |
  |-- Scan QR in app          |                  |
  |                           |                  |
  | (loop every 30s)          |                  |
  |-- POST /mfa/totp/verify -->|                  |
  |   code=123456             |-- Decrypt secret |
  |                           |-- Verify TOTP == code
  |                           |-- UPDATE verified=true (first time)
  |<-- { verified: true } ----|                  |
```

---

## API Summary by Protocol

### OAuth 2.0 (`backend/oauth-oidc/`)

| Method | Path | Grant |
|--------|------|-------|
| `GET` | `/oauth2/authorize` | — |
| `POST` | `/oauth2/token` | `authorization_code`, `client_credentials`, `refresh_token`, `urn:ietf:params:oauth:grant-type:device_code` |
| `POST` | `/oauth2/introspect` | RFC 7662 |
| `POST` | `/oauth2/revoke` | RFC 7009 |
| `GET` | `/.well-known/openid-configuration` | RFC 8414 |
| `GET` | `/.well-known/jwks.json` | RFC 7517 |
| `POST` | `/.well-known/jwks.json` | Key rotation |
| `GET` | `/userinfo` | OIDC Core |

### SCIM 2.0 (`backend/scim/`)

| Method | Path | RFC |
|--------|------|-----|
| `POST` | `/scim/v2/Users` | §5.2 |
| `GET` | `/scim/v2/Users` | §5.2 |
| `GET` | `/scim/v2/Users/{id}` | §5.2 |
| `PUT` | `/scim/v2/Users/{id}` | §5.2 |
| `DELETE` | `/scim/v2/Users/{id}` | §5.2 |
| `POST` | `/scim/v2/Groups` | §5.3 |
| `GET` | `/scim/v2/Groups` | §5.3 |
| `GET` | `/scim/v2/Groups/{id}` | §5.3 |
| `PATCH` | `/scim/v2/Groups/{id}` | §5.3 |

### SAML 2.0 (`backend/saml-federation/`)

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/saml/metadata` | Signed SP metadata XML |
| `GET` | `/saml/initiate` | Builds and redirects with signed AuthnRequest |
| `POST` | `/saml/acs` | Receives SAMLResponse from IdP |

### MFA (`backend/mfa/`)

| Method | Path | RFC |
|--------|------|-----|
| `POST` | `/mfa/totp/setup` | RFC 6238 |
| `POST` | `/mfa/totp/verify` | RFC 6238 |
| `GET` | `/mfa/totp/status` | — |
| `POST` | `/webauthn/register/begin` | W3C WebAuthn |
| `POST` | `/webauthn/register/complete` | W3C WebAuthn |
| `POST` | `/webauthn/authenticate/begin` | W3C WebAuthn |
| `POST` | `/webauthn/authenticate/complete` | W3C WebAuthn |

### Device Flow (`backend/device-flow/`)

| Method | Path | RFC |
|--------|------|-----|
| `POST` | `/device_authorization` | RFC 8628 §3.1 |
| `GET` | `/device` | RFC 8628 §3.2 (user approval page) |
| `POST` | `/device/approve` | RFC 8628 §3.3 |
