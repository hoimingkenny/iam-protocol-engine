# IAM Protocol Engine

Portfolio-grade enterprise IAM platform demonstrating RFC-level protocol knowledge — built from scratch, not a Keycloak wrapper.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16, Redis 7, React 19 + Vite + MUI

---

## What This Project Is

Every endpoint, token, and assertion is deliberately built to demonstrate RFC-level understanding. The goal is protocol-level depth, not library usage.

| Protocol | RFC | Status |
|----------|-----|--------|
| OAuth 2.0 Authorization Code + PKCE | RFC 7636, 6749 | **Phase 2 ✓** |
| OAuth 2.0 Client Credentials | RFC 6749 §4.2 | **Phase 2 ✓** |
| OAuth 2.0 Refresh Token Rotation | RFC 6749 §6 | **Phase 2 ✓** |
| OAuth 2.0 Device Authorization Grant | RFC 8628 | Phase 3 |
| OpenID Connect (ID token, discovery, JWKS) | RFC 9068 | Phase 3 |
| SAML 2.0 Federation | OASIS SAML 2.0 | Phase 4 |
| SCIM 2.0 | RFC 7644 | Phase 5 |
| TOTP (Google Authenticator) | RFC 6238 | Phase 6 |
| WebAuthn / FIDO2 | W3C WebAuthn L3 | Phase 6 |

---

## Prerequisites

- **Java 21** — [`sdk install java 21.0.3-tem`](https://sdkman.io/)
- **Docker & Docker Compose** — PostgreSQL 16 + Redis 7 run via `docker compose`
- **Maven 3.9+** — wrapped (`./mvnw`) or system-wide
- **PostgreSQL client** (`psql`) — optional, for manual DB inspection

---

## Quick Start

### 1. Start infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d
```

This starts **PostgreSQL 16** on `localhost:5432` and **Redis 7** on `localhost:6379`.

### 2. Run database migrations

```bash
./mvnw flyway:migrate -pl backend/auth-core
```

### 3. Build and test

```bash
./mvnw clean install
./mvnw test
```

### 4. Run the application

```bash
./mvnw spring-boot:run -pl backend/api-gateway
```

The API gateway starts on **http://localhost:8080**.

---

## Testing Guide

### Run All Tests

```bash
./mvnw test
```

### Run Tests by Module

```bash
./mvnw test -pl backend/auth-core        # 13 tests: entity validation
./mvnw test -pl backend/oauth-oidc       # 44 tests: PKCE, authorize, token
./mvnw test -pl backend/demo-resource     # 7 tests: bearer token filter
./mvnw test -pl backend/api-gateway      # 3 tests: application context
```

### Manual API Testing

Start the server first, then use these curl commands:

```bash
./mvnw spring-boot:run -pl backend/api-gateway
```

**Register a test client** in PostgreSQL first:

```sql
INSERT INTO oauth_client (
    client_id, client_secret_hash, client_name,
    redirect_uris, allowed_scopes, grant_types, is_public
) VALUES (
    'test-client',
    -- SHA-256 of 'test-secret' in base64
    'jQAi4xF+BNLzj/cxD5aHJ1M5qF8aLmP7bQ3vR6hX0Wk=',
    'Test Client',
    'http://localhost:9000/callback',
    'openid profile email',
    'authorization_code client_credentials refresh_token',
    false
);
```

> To generate the hash: `echo -n 'test-secret' | openssl dgst -sha256 -binary | base64`

#### Auth Code + PKCE Flow

```bash
# Step 1: Get an auth code (RFC 7636 Appendix B test vector)
VERIFIER="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
CHALLENGE="E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

curl -s "http://localhost:8080/oauth2/authorize?\
  client_id=test-client&\
  redirect_uri=http://localhost:9000/callback&\
  response_type=code&\
  scope=openid&\
  state=abc123&\
  code_challenge=${CHALLENGE}&\
  code_challenge_method=S256&\
  subject=user1" -D - | grep -E "^location:"

# Extract the code from: location: http://localhost:9000/callback?code=XXX&state=abc123
CODE="<extracted-code>"

# Step 2: Exchange code for tokens
curl -s -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=authorization_code" \
  -d "code=${CODE}" \
  -d "redirect_uri=http://localhost:9000/callback" \
  -d "client_id=test-client" \
  -d "code_verifier=${VERIFIER}"
```

Expected response:
```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshToken": "..."
}
```

#### Client Credentials Flow

```bash
curl -s -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=client_credentials" \
  -d "client_id=test-client" \
  -d "client_secret=test-secret" \
  -d "scope=openid profile"
```

#### Refresh Token Rotation

```bash
# First refresh
RESP=$(curl -s -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<refresh_token>")
echo $RESP | jq .

# Second refresh (old token revoked — this still works)
RESP2=$(curl -s -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<refresh_token>")
echo $RESP2 | jq .
```

#### demo-resource Protected API

```bash
# No token → 401
curl -s http://localhost:8080/api/resource

# Valid token → 200
curl -s http://localhost:8080/api/resource \
  -H "Authorization: Bearer <access_token>"

# Health endpoint → 200 (no auth)
curl -s http://localhost:8080/api/health
```

### Frontend (Documentation Site)

```bash
cd frontend/app
npm install
npm run start       # Dev server on http://localhost:3000
npm run build       # Production build
```

---

## Architecture

```
backend/
├── auth-core/          ← JPA entities, repositories, AuditService, Flyway migrations
├── oauth-oidc/         ← /authorize, /token, PKCE (Phase 2 ✓)
├── saml-federation/    ← SAML SP, ACS, SAML→OIDC bridge (Phase 4)
├── scim/               ← SCIM 2.0 /Users and /Groups (Phase 5)
├── mfa/                ← TOTP + WebAuthn (Phase 6)
├── device-flow/         ← RFC 8628 Device Authorization Grant (Phase 3)
├── demo-resource/      ← Protected sample API (Phase 2 ✓)
└── api-gateway/        ← Single SpringBootApplication entry point
```

**`auth-core`** is the only module with JPA entities and repositories. All other modules depend on it.

### Data Flow

```
Client → api-gateway → oauth-oidc → auth-core (entities/repos)
                              ↓
                        Redis (short-lived state: auth codes, device codes)
                              ↓
                        PostgreSQL (source of truth: clients, tokens, users)
```

---

## Key Commands

### Infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d     # Start
docker compose -f infra/docker-compose.yml down       # Stop
docker compose -f infra/docker-compose.yml logs -f     # Tail logs
```

### Database

```bash
./mvnw flyway:migrate -pl backend/auth-core          # Run migrations
./mvnw flyway:baseline -pl backend/auth-core         # Add baseline (if needed)
./mvnw flyway:clean -pl backend/auth-core            # Drop all tables (dev only)
```

### Building

```bash
./mvnw clean install                                  # Build all modules
./mvnw test                                           # Run all tests
./mvnw test -pl backend/oauth-oidc                    # Test single module
./mvnw spring-boot:run -pl backend/api-gateway       # Run gateway
```

### Database Inspection

```bash
psql -h localhost -U iam_user -d iam_engine -c '\dt'          # List tables
psql -h localhost -U iam_user -d iam_engine -c '\d oauth_client'  # Describe table
psql -h localhost -U iam_user -d iam_engine -c 'SELECT * FROM token LIMIT 5'  # Check tokens
```

---

## Schema

All tables live in the `public` schema of `iam_engine` (PostgreSQL):

| Table | Description |
|-------|-------------|
| `oauth_client` | Client registry — `client_id`, hashed secret, redirect URIs, scopes, grant types |
| `auth_code` | Short-lived authorization codes with PKCE challenge (5-min TTL) |
| `token` | Access / refresh tokens — JTI, subject, expiry, revoked flag |
| `scim_user` | SCIM user — username, emails, groups, attributes (JSONB) |
| `scim_group` | SCIM group — display name, members |
| `webauthn_credential` | FIDO2 credential — credential ID, public key, sign count |
| `totp_credential` | TOTP secret — Base32 secret, digits, period, algorithm |
| `device_code` | RFC 8628 device flow — user code, status, polling count |
| `directory_link` | Hybrid identity — LDAP / Entra ID user linkage |
| `audit_event` | Structured audit log — event type, actor, subject, JTI, IP, details (JSONB) |

---

## Configuration

Environment variables (set in `infra/.env` for local development):

| Variable | Description |
|----------|-------------|
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `REDIS_PASSWORD` | Redis password |

**Never commit `.env`** — it's gitignored. Use `.env.example` as a template.

---

## Project Documentation

| Document | Description |
|----------|-------------|
| `SPEC.md` | Full project specification — 9 phases, API contracts, data model |
| `IMPLEMENTATION_PLAN.md` | 27-task breakdown across 9 phases with acceptance criteria |
| `frontend/app/docs/` | Learning site — chapter-by-chapter protocol walkthroughs |

---

## Current Status

**Phase 2 (OAuth 2.0 Core) — Complete**

- `AuthorizeService` + `AuthorizeController`: GET `/oauth2/authorize` with PKCE validation
- `TokenService` + `TokenController`: POST `/oauth2/token` (authorization_code, client_credentials, refresh_token)
- `PkceUtils`: RFC 7636 code_verifier / code_challenge generation and verification
- `demo-resource`: Bearer token filter, `GET /api/resource`, `GET /api/health`
- 71 tests passing (auth-core 13 + oauth-oidc 44 + demo-resource 7 + api-gateway 3)

**Next:** Phase 3 — OIDC Layer (ID token with RS256, OIDC discovery, JWKS, `/userinfo`)

---

## Branch Structure

```
main          ← Stable releases
  └── phase-2 ← OAuth 2.0 Core (current PR)
        ├── phase-2-task5-pkce
        ├── phase-2-task6-authorize
        ├── phase-2-task7-token
        ├── phase-2-task8-client-credentials  (merged into task 7)
        └── phase-2-task9-demo-resource
```
