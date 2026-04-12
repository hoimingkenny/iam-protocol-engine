# IAM Protocol Engine — Specification

> [!important]
> This spec is the source of truth. All implementation must trace back to these sections.

---

## Objective

Build a portfolio-grade enterprise IAM platform that demonstrates RFC-level protocol knowledge:
- OAuth 2.0 (Auth Code + PKCE, Client Credentials)
- OIDC (ID token, discovery, JWKS, userinfo)
- SAML 2.0 (SP-initiated SSO, SAML → OIDC bridge)
- SCIM 2.0 (User/Group CRUD, joiner/mover/leaver)
- WebAuthn + TOTP MFA
- Device Authorization Grant (RFC 8628)

**Primary user:** Enterprise IAM architects, hiring managers, technical interviewers.
**Goal:** End-to-end flows work and can be explained in a demo or interview setting.
**Why this vs. Keycloak:** The point is to understand *why* Keycloak behaves the way it does — not to replace it.

**Stack:** Java 21, Spring Boot 3.3, Spring Authorization Server 1.3 (scaffolding only), React 19 + Vite + MUI, PostgreSQL 16, Redis 7, Docker Compose.

---

## Commands

```bash
# Backend
./mvnw clean install                    # Build all modules
./mvnw test                             # Run tests
./mvnw test -pl backend/oauth-oidc      # Test a single module
./mvnw spring-boot:run -pl backend/api-gateway  # Run gateway (starts all modules)

# Frontend
cd frontend && npm install
npm run dev                             # Vite dev server
npm run build                           # Production build

# Infrastructure
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL + Redis

# Database migrations (Flyway)
./mvnw flyway:migrate -pl backend/auth-core
```

---

## Project Structure

```
iam-protocol-engine/
├── SPEC.md                             # This file
├── README.md                           # Architecture overview, end-to-end demo guide
├── pom.xml                             # Parent POM
├── infra/
│   └── docker-compose.yml              # PostgreSQL 16 + Redis 7
├── backend/
│   ├── pom.xml                         # Backend parent POM
│   ├── auth-core/                      # Shared JPA entities, AuditService, base repository
│   │   └── src/main/resources/
│   │       └── db/migration/          # Flyway migrations (V1__init.sql, etc.)
│   ├── oauth-oidc/                     # OAuth 2.0 + OIDC endpoints
│   │   └── src/main/java/.../oauth/
│   ├── saml-federation/                # SAML 2.0 SP + SAML→OIDC bridge
│   ├── scim/                           # SCIM 2.0 /Users /Groups
│   ├── mfa/                            # TOTP + WebAuthn
│   ├── device-flow/                    # RFC 8628 Device Authorization Grant
│   ├── demo-resource/                  # Protected sample API (validates access tokens)
│   └── api-gateway/                    # Single @SpringBootApplication entry point
└── frontend/
    ├── app/                            # React + TypeScript + Vite
    │   ├── src/
    │   │   ├── pages/                  # Login, Admin, Audit, Playground
    │   │   ├── components/             # Shared MUI components
    │   │   └── lib/                    # Auth SDK, API client
    │   └── package.json
    └── package.json                    # Workspace root
```

---

## Module Responsibilities

| Module | Responsibility |
|--------|----------------|
| `auth-core` | JPA entities (OAuthClient, Token, ScimUser, WebAuthnCredential, TotpCredential, DeviceCode, AuditEvent). AuditService interface. Base repository. |
| `oauth-oidc` | `/authorize`, `/token` (auth code, client credentials, refresh), `/introspect`, `/revoke`, OIDC discovery, JWKS, ID token, `/userinfo` |
| `saml-federation` | SP metadata, AuthnRequest, ACS, assertion validation, SAML→OIDC claim mapping |
| `scim` | `/scim/v2/Users`, `/scim/v2/Groups` — full CRUD + joiner/mover/leaver |
| `mfa` | TOTP enrollment + verification, WebAuthn registration + assertion |
| `device-flow` | `/device_authorization`, `/device` approval page, `/token` device grant exchange |
| `demo-resource` | Protected REST API that validates access tokens |
| `api-gateway` | Spring Boot entry point, wires all modules together |

---

## Data Model

```text
OAuthClient
  client_id (PK), client_secret (hashed), redirect_uris[], allowed_scopes[],
  grant_types[], created_at, updated_at

AuthCode
  code (PK), client_id (FK), subject (user_id), code_challenge, scope,
  expires_at, consumed_at

Token
  jti (PK), type (access/refresh/id), client_id (FK), subject (user_id),
  scope[], expires_at, revoked, issued_at

ScimUser
  id (PK, UUID), username, emails[], display_name, active,
  groups[], attributes (JSON), created_at, updated_at

ScimGroup
  id (PK, UUID), display_name, members[ScimUser.id], created_at, updated_at

WebAuthnCredential
  credential_id (PK), user_id (FK), public_key_cose, sign_count,
  aaguid, created_at

TotpCredential
  id (PK), user_id (FK), secret (encrypted), verified, created_at

DeviceCode
  device_code (PK), user_code, client_id (FK), scope[], status, expires_at, approved_by

AuditEvent
  id (PK), event_type, actor, subject, client_id, scope, jti,
  ip_address, timestamp, details (JSON)
```

---

## API Design

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/authorize` | Auth Code + PKCE entry point |
| POST | `/token` | Auth code, client credentials, refresh, device grant exchange |
| POST | `/introspect` | Token validation for resource servers |
| POST | `/revoke` | Token revocation |
| GET | `/.well-known/openid-configuration` | OIDC discovery |
| GET | `/.well-known/jwks.json` | Public signing keys (RS256, kid) |
| GET | `/userinfo` | OIDC claims endpoint |
| GET | `/saml/metadata` | SP metadata |
| GET | `/saml/initiate` | SAML SP-initiated SSO start |
| POST | `/saml/acs` | SAML Assertion Consumer Service |
| POST | `/scim/v2/Users` | Create SCIM user (joiner) |
| GET | `/scim/v2/Users` | List/search SCIM users |
| GET | `/scim/v2/Users/{id}` | Get SCIM user |
| PUT | `/scim/v2/Users/{id}` | Replace SCIM user (mover) |
| DELETE | `/scim/v2/Users/{id}` | Delete SCIM user (leaver) |
| POST | `/scim/v2/Groups` | Create SCIM group |
| GET | `/scim/v2/Groups` | List/search SCIM groups |
| GET | `/scim/v2/Groups/{id}` | Get SCIM group |
| PATCH | `/scim/v2/Groups/{id}` | Modify SCIM group membership |
| POST | `/device_authorization` | Device flow start |
| GET | `/device` | Human approval page for device flow |
| POST | `/webauthn/register/begin` | WebAuthn registration challenge |
| POST | `/webauthn/register/complete` | WebAuthn registration completion |
| POST | `/webauthn/authenticate/begin` | WebAuthn authentication challenge |
| POST | `/webauthn/authenticate/complete` | WebAuthn authentication verification |
| POST | `/mfa/totp/setup` | TOTP enrollment |
| POST | `/mfa/totp/verify` | TOTP verification |

---

## Code Style

**Java naming:** Standard Java conventions (`camelCase` methods, `PascalCase` classes, `SCREAMING_SNAKE_CASE` constants). No Hungarian notation.

**Spring endpoints follow RFC naming:** `authorization_code`, `refresh_token`, `client_credentials` — match RFC 6749/7636/8628 token types exactly.

**Error responses:** RFC-compliant OAuth error format:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri mismatch"
}
```

**ID token claims (minimum):** `iss`, `sub`, `aud`, `exp`, `iat`, `nonce`

**Structured logging fields always present:** `client_id`, `sub`, `scope`, `jti`, `event_type`

---

## Testing Strategy

| Level | Scope | Location |
|-------|-------|----------|
| Unit | JWT signing/validation, PKCE logic, claim mapping, SCIM schema | `src/test/java/.../` per module |
| Integration | Auth code exchange, token introspection, SCIM CRUD, SAML assertion | `src/test/java/.../integration/` |
| E2E | Full auth flows via HTTP client, Real DB + Redis | `src/test/java/.../e2e/` |

**Coverage gate:** ≥ 70% line coverage on `oauth-oidc` and `scim` modules.

---

## Boundaries

**Always:**
- RS256 signing; `kid` in JWKS from day one
- PKCE required for public clients
- `redirect_uri` exact match (no pattern matching)
- Structured audit logs with `client_id`, `sub`, `jti`
- Flyway migrations; no raw SQL in application code
- Redis TTL for all short-lived state (auth codes, device codes, nonce)

**Ask first:**
- Changing token lifetimes
- Adding new grant types
- Modifying the data model (new entity or column)
- Adding external IdP configuration

**Never:**
- Commit secrets or real credentials to source control
- Use implicit flow or hybrid flow
- Use symmetric signing (HS256) for production tokens
- Bypass signature validation on SAML assertions
- Store TOTP secrets in plaintext

---

## Success Criteria

| ID | Criterion | Verification |
|----|-----------|--------------|
| SC-01 | Auth Code + PKCE flow completes end-to-end; tokens issued | Integration test with real /token endpoint |
| SC-02 | Client Credentials flow issues valid access token | `curl` against `/token` with `grant_type=client_credentials` |
| SC-03 | Refresh token rotation invalidates old token | Two sequential refresh calls; second invalidates first |
| SC-04 | `/introspect` returns `{"active":true}` for valid token, `{"active":false}` for revoked | Integration test |
| SC-05 | OIDC discovery returns correct `issuer`, `jwks_uri`, `response_types_supported` | `curl /.well-known/openid-configuration` |
| SC-06 | JWKS endpoint contains valid RSA public key with `kid` | `curl /.well-known/jwks.json` |
| SC-07 | ID token is valid RS256 JWT with required claims | Decode and verify signature against JWKS |
| SC-08 | SCIM `/Users` CRUD operations return RFC-compliant responses | Integration tests against each HTTP method |
| SC-09 | SCIM joiner → token revocation → leaver flow works | E2E test |
| SC-10 | Device Authorization Grant completes (RFC 8628) | `curl` device flow sequence |
| SC-11 | WebAuthn registration creates stored credential | Integration test |
| SC-12 | WebAuthn assertion verification succeeds with valid credential | Integration test |
| SC-13 | TOTP verification succeeds with correct code | Integration test |
| SC-14 | Protected demo API rejects requests without valid Bearer token | `curl /api/resource` without token → 401 |
| SC-15 | Docker Compose starts PostgreSQL + Redis | `docker compose up -d` |

---

## Open Questions

1. Entra ID or Keycloak as primary SAML IdP for integration testing?
2. Keep SCIM in a separate DB schema boundary from auth-core?
3. Frontend auth storage — session cookie or localStorage JWT?

**Decided:** Keycloak as primary SAML IdP (faster local testing); Entra ID as secondary for demo realism.

---

## Related Docs

- `doc/01_PRODUCT-BRIEF.md` — Vision, why not Keycloak, product positioning
- `doc/02_SYSTEM-ARCHITECTURE.md` — Full technical brief, data model, NFRs
- `doc/study/01_LEARNING-AND-INTERVIEW-NOTES.md` — Interview angles, protocol rationale
