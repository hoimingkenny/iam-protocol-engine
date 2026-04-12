# IAM Protocol Engine — Implementation Plan

**Spec:** `SPEC.md`
**Updated:** 2026-04-11

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Spring Authorization Server as scaffolding only | Core protocol logic written by hand — Spring handles wire-format, not behavior |
| RS256 (self-built JWS/JWT) | Standard RS256; `kid` in JWKS from day 1 for rotation; built by hand to demonstrate RFC-level understanding — no JWT library |
| PostgreSQL for short-lived state | Auth codes, nonce, device flow polling state, revocation cache; Redis reserved for future caching needs |
| PostgreSQL as source of truth | Long-lived entities (clients, tokens, users) |
| SCIM built manually (no SDK) | Protocol depth; SDK would hide the spec |
| Keycloak as primary SAML IdP for testing | Fast local iteration; Entra ID as demo realism |

---

## Dependency Graph

```
Docker Compose (PostgreSQL + Redis)
    │
    ▼
Parent POM + Module POMs
    │
    ▼
auth-core (entities, AuditService, base repo)
    │
    ├──────────────────────────────┐
    ▼                              ▼
oauth-oidc                   demo-resource
    │                              │
    │                              ▼
    │                     Token validation filter
    │
    ▼
OIDC discovery + JWKS
    │
    ▼
Token lifecycle (refresh, revoke, introspect)
    │
    ▼
SCIM 2.0
    │
    ▼
SAML 2.0 + SAML→OIDC bridge
    │
    ▼
MFA (TOTP + WebAuthn) + Device Flow
    │
    ▼
Frontend Admin UI (can start after oauth-oidc)
```

---

## Task List

---

### Phase 1: Bootstrap

**Goal:** Project skeleton, infra, base entities. Leaves system in a compilable, runnable state.

---

#### Task 1: Parent POM + Module Skeleton

**Description:** Create the Maven multi-module project structure with all 8 backend modules and the frontend workspace root.

**Acceptance criteria:**
- [ ] `pom.xml` at root with `<modules>` listing all 8 backend modules
- [ ] Each module has its own `pom.xml` with correct `<parent>` and `<artifactId>`
- [ ] All modules compile individually with `./mvnw compile`
- [ ] No external dependencies beyond Spring Boot BOM in any module yet

**Verification:** `./mvnw compile` succeeds with no errors across all modules.

**Dependencies:** None

**Files:**
- `pom.xml`
- `backend/pom.xml`
- `backend/auth-core/pom.xml`
- `backend/oauth-oidc/pom.xml`
- `backend/saml-federation/pom.xml`
- `backend/scim/pom.xml`
- `backend/mfa/pom.xml`
- `backend/device-flow/pom.xml`
- `backend/demo-resource/pom.xml`
- `backend/api-gateway/pom.xml`
- `frontend/package.json`

**Estimated scope:** S

---

#### Task 2: Docker Compose + Flyway Init

**Description:** Set up `infra/docker-compose.yml` with PostgreSQL 16 and Redis 7, plus the Flyway migrations directory and `V1__init.sql`.

**Acceptance criteria:**
- [ ] `docker compose -f infra/docker-compose.yml up -d` starts PostgreSQL and Redis with correct ports
- [ ] `docker compose ps` shows both containers healthy
- [ ] `V1__init.sql` creates all tables: `oauth_client`, `auth_code`, `token`, `scim_user`, `scim_group`, `webauthn_credential`, `totp_credential`, `device_code`, `audit_event`
- [ ] `./mvnw flyway:migrate -pl backend/auth-core` runs and reports success

**Verification:** `docker compose up -d` + `flyway:migrate` succeed; tables exist in PostgreSQL.

**Dependencies:** Task 1

**Files:**
- `infra/docker-compose.yml`
- `backend/auth-core/src/main/resources/db/migration/V1__init.sql`

**Estimated scope:** M

---

#### Task 3: auth-core Entities + AuditService

**Description:** JPA entity classes for all tables from Task 2, plus `AuditService` interface and a base `JpaRepository` setup.

**Acceptance criteria:**
- [ ] All entity classes annotate correct columns, lengths, constraints
- [ ] `OAuthClient.client_id` is PK; `redirect_uris`, `allowed_scopes`, `grant_types` stored as comma-separated or JSON column
- [ ] `AuditService.audit(event)` method exists and is called from auth code issuance and token issuance
- [ ] `./mvnw compile -pl backend/auth-core` succeeds

**Verification:** Compilation succeeds; entities map to V1__init.sql tables correctly (no column mismatches).

**Dependencies:** Tasks 1, 2

**Files:**
- `backend/auth-core/src/main/java/.../entity/OAuthClient.java`
- `backend/auth-core/src/main/java/.../entity/AuthCode.java`
- `backend/auth-core/src/main/java/.../entity/Token.java`
- `backend/auth-core/src/main/java/.../entity/ScimUser.java`
- `backend/auth-core/src/main/java/.../entity/ScimGroup.java`
- `backend/auth-core/src/main/java/.../entity/WebAuthnCredential.java`
- `backend/auth-core/src/main/java/.../entity/TotpCredential.java`
- `backend/auth-core/src/main/java/.../entity/DeviceCode.java`
- `backend/auth-core/src/main/java/.../entity/AuditEvent.java`
- `backend/auth-core/src/main/java/.../service/AuditService.java`
- `backend/auth-core/src/main/java/.../repository/...`

**Estimated scope:** M

---

#### Task 4: api-gateway Entry Point

**Description:** `api-gateway` as the single `@SpringBootApplication`. Configures PostgreSQL, Redis, JPA, and component scanning for all modules.

**Acceptance criteria:**
- [ ] `ApiGatewayApplication.java` has `@SpringBootApplication`, scans all modules
- [ ] `application.yml` configures PostgreSQL (port 5432), Redis (port 6379), JPA `ddl-auto=none`
- [ ] `./mvnw spring-boot:run -pl backend/api-gateway` starts and connects to Docker services
- [ ] Health endpoint returns 200

**Verification:** App starts; `curl localhost:8080/actuator/health` returns `{"status":"UP"}`.

**Dependencies:** Tasks 1, 2, 3

**Files:**
- `backend/api-gateway/src/main/java/.../ApiGatewayApplication.java`
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/api-gateway/pom.xml` (JDBC, JPA, Redis starters)

**Estimated scope:** S

---

### Checkpoint: Phase 1 ✅

- [x] All 4 tasks pass verification
- [x] `./mvnw compile` clean across all modules
- [x] App starts and connects to PostgreSQL + Redis
- [x] Flyway migration applied
- [x] Human reviews before proceeding to Phase 2

---

### Learning Site: Phase 1 Chapters

**Location:** `frontend/app/` (Docusaurus)
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

Chapters written alongside Phase 1 code:

| Chapter | Source |
|---------|--------|
| `01-Bootstrap/00-Overview` | `doc/codechange/01_P1-CODE-CHANGE-SUMMARY.md` |
| `01-Bootstrap/01-Maven-Modules` | Phase 1 task docs |
| `01-Bootstrap/02-Docker-Compose` | Phase 1 task docs |
| `01-Bootstrap/03-JPA-Entities` | Phase 1 task docs |
| `01-Bootstrap/04-API-Gateway` | Phase 1 task docs |
| `01-Bootstrap/05-Tests` | Phase 1 task docs |
| `00-Introduction/01-Why-This-Project` | `doc/01_PRODUCT-BRIEF.md` |

Reference docs migrated: System Architecture, Learning & Interview Notes, Spec, Implementation Plan.

---

### Phase 2: OAuth 2.0 Core

**Goal:** Auth Code + PKCE and Client Credentials flows fully functional.

---

#### Task 5: PKCE Utility + Code Generation

**Description:** Utility class for PKCE: `code_verifier` generation (43-128 char random), S256 `code_challenge` computation via SHA-256 + Base64URL (RFC 7636).

**Acceptance criteria:**
- [x] `code_challenge = BASE64URL(SHA256(code_verifier))` matches RFC 7636 test vectors
- [x] `PkceUtils.generateCodeVerifier()` returns 43-128 char URL-safe random string
- [x] `PkceUtils.deriveCodeChallenge(verifier)` returns S256 challenge
- [x] `PkceUtils.verifyCodeChallenge(verifier, challenge, method)` validates correctly
- [x] Unit tests for both with known test vectors

**Verification:** `./mvnw test -pl backend/oauth-oidc -Dtest=PkceUtilsTest` passes.

**Dependencies:** Task 4

**Files:**
- `backend/oauth-oidc/src/main/java/.../util/PkceUtils.java`

**Estimated scope:** S

---

#### Task 6: /authorize Endpoint (Auth Code + PKCE)

**Description:** `GET /authorize` handler. Validates `client_id`, `redirect_uri` (exact match), `response_type=code`, `scope`, `state`. Stores auth code in PostgreSQL with TTL. Issues redirect with `code` and `state`.

**Acceptance criteria:**
- [x] Returns 400 with `invalid_request` if `redirect_uri` does not exactly match registered URI
- [x] Returns 400 with `invalid_request` if `code_challenge` missing or no `code_challenge_method=S256`
- [x] Returns 302 redirect to `redirect_uri?code=AUTHCODE&state=STATE` on success
- [x] Auth code stored in PostgreSQL with 5-minute TTL
- [x] Audit event logged

**Verification:** `curl -v "http://localhost:8080/authorize?client_id=test&redirect_uri=https://app.example.com/cb&response_type=code&scope=openid&state=xyz&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"` → 302 with code.

**Dependencies:** Tasks 4, 5

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/AuthorizeController.java`
- `backend/oauth-oidc/src/main/java/.../service/AuthorizeService.java`

**Estimated scope:** M

---

#### Task 7: /token Endpoint — Auth Code Exchange

**Description:** `POST /token` handler for `grant_type=authorization_code`. Validates `code`, `code_verifier` against stored PKCE challenge. Issues access token + refresh token. Marks auth code consumed.

**Acceptance criteria:**
- [x] Returns 400 `invalid_grant` if code already consumed or expired
- [x] Returns 400 `invalid_grant` if `code_verifier` doesn't match stored challenge
- [x] Issues opaque access token (random string stored in DB); RS256 JWT access tokens are Phase 3
- [x] Issues refresh token (opaque random string) stored in PostgreSQL with 7-day TTL
- [x] Auth code consumed atomically (cannot be reused)
- [x] Audit event logged

**Verification:** Use Task 6 code → `curl -X POST http://localhost:8080/oauth2/token -d "grant_type=authorization_code&code=AUTHCODE&code_verifier=codeverifier&redirect_uri=https://app.example.com/cb"` → opaque access token + refresh token.

**Dependencies:** Tasks 5, 6

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/TokenController.java`
- `backend/oauth-oidc/src/main/java/.../service/TokenService.java`

**Estimated scope:** M

---

#### Task 8: /token Endpoint — Client Credentials

**Description:** `POST /token` with `grant_type=client_credentials`. Authenticates client via `client_id` + `client_secret` (basic auth or body). Issues access token scoped to client's allowed scopes.

**Acceptance criteria:**
- [x] Authenticates via `Authorization: Basic base64(id:secret)` header
- [x] Returns 400 `invalid_client` if credentials don't match
- [x] Issues access token with `client_id` as `sub` claim (no user)
- [x] Scope limited to client's `allowed_scopes`
- [x] Audit event logged

**Verification:** `curl -X POST http://localhost:8080/token -H "Authorization: Basic $(echo -n client1:secret1 | base64)" -d "grant_type=client_credentials&scope=api:read"` → opaque access token.

**Dependencies:** Tasks 4, 7 (TokenService reused)

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/TokenController.java` (adds client_credentials branch to TokenService)

---

#### Task 9: demo-resource Protected API

**Description:** `demo-resource` module with a `/api/resource` endpoint that validates Bearer token on every request. Returns 401 if missing or invalid, 200 with resource data if valid.

**Acceptance criteria:**
- [x] `GET /api/resource` requires `Authorization: Bearer <token>`
- [x] Returns 401 if no token or invalid token
- [x] Validates token against DB (not just signature): checks `active=true`, not expired, not revoked
- [x] Returns 200 with JSON payload on valid token
- [x] Audit event logged on each call

**Verification:** `curl http://localhost:8080/api/resource` → 401; `curl -H "Authorization: Bearer <valid_token>" http://localhost:8080/api/resource` → 200 + JSON.

**Dependencies:** Tasks 7, 8

**Files:**
- `backend/demo-resource/src/main/java/.../controller/ResourceController.java`
- `backend/demo-resource/src/main/java/.../security/BearerTokenAuthenticationFilter.java`
- `backend/demo-resource/src/main/java/.../security/ResourceSecurityConfig.java`
- `backend/demo-resource/src/main/java/.../security/TokenValidationService.java`

**Estimated scope:** M

---

### Checkpoint: Phase 2 ✅

- [x] Auth Code + PKCE flow end-to-end works (Tasks 6 + 7)
- [x] Client Credentials flow works (Task 8)
- [x] demo-resource validates tokens correctly (Task 9)
- [x] `./mvnw test -pl backend/oauth-oidc,demo-resource` passes
- [x] Human reviews before Phase 3

---

### Learning Site: Phase 2 Chapters

**Location:** `frontend/app/docs/02-OAuth2/`
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

| Chapter | Source |
|---------|--------|
| `02-OAuth2/00-Overview` | Phase 2 overview, what OAuth 2.0 adds over basic auth |
| `02-OAuth2/01-PKCE` | RFC 7636, code_challenge, code_verifier, S256 |
| `02-OAuth2/02-Authorize-Endpoint` | RFC 6749 §4.1, redirect_uri exact match, state, errors |
| `02-OAuth2/03-Token-Endpoint` | RFC 6749 §3.2, auth code exchange, token response |
| `02-OAuth2/04-Client-Credentials` | RFC 6749 §4.2, client authentication, m2m tokens |
| `02-OAuth2/05-Demo-Resource` | Bearer token validation, demo API protected endpoint |

---

### Phase 3: OIDC Layer

**Goal:** OIDC discovery, JWKS, ID token, userinfo.

---

#### Task 10: OIDC Discovery + JWKS

**Description:** `/.well-known/openid-configuration` returns RFC 8414 metadata. `/.well-known/jwks.json` returns RSA public key(s) with `kid`.

**Acceptance criteria:**
- [x] `/.well-known/openid-configuration` returns correct `issuer`, `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `response_types_supported`, `subject_types_supported`, `id_token_signing_alg_values_supported`
- [x] `/.well-known/jwks.json` contains RSA public key with `kid`, `use=sig`, `alg=RS256`
- [x] New key pair generated at startup; `kid` stable across restarts (derived from key thumbprint)
- [x] Admin endpoint to trigger key rotation (generates new key, adds to JWKS, old key still valid for validation)

**Verification:** `curl http://localhost:8080/.well-known/openid-configuration | jq .` and `curl http://localhost:8080/.well-known/jwks.json | jq .`

**Dependencies:** Task 7

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/DiscoveryController.java`
- `backend/oauth-oidc/src/main/java/.../controller/JwksController.java`
- `backend/oauth-oidc/src/main/java/.../security/JwksService.java`
- `backend/oauth-oidc/src/main/java/.../security/RsaKeyPairGenerator.java`

**Estimated scope:** M

---

#### Task 11: ID Token Issuance

**Description:** When auth code is exchanged (Task 7), also issue an ID token (RS256 JWT) alongside the access token. ID token claims: `iss`, `sub`, `aud` (client_id), `exp`, `iat`, `nonce`.

**Acceptance criteria:**
- [x] ID token is RS256-signed JWT
- [x] Contains all required claims: `iss`, `sub`, `aud`, `exp`, `iat`, `nonce`
- [x] `nonce` value matches what was passed to `/authorize`
- [x] ID token returned in `/token` response as `id_token` field
- [x] ID token can be validated against JWKS endpoint

**Verification:** Exchange auth code → `id_token` in response. Decode JWT, verify signature against JWKS, verify `nonce` claim.

**Dependencies:** Tasks 7, 10

**Files:**
- `backend/oauth-oidc/src/main/java/.../security/IdTokenGenerator.java`
- `backend/oauth-oidc/src/main/java/.../service/TokenService.java` (modify to include ID token)

**Estimated scope:** M

---

#### Task 12: /userinfo Endpoint

**Description:** `GET /userinfo` returns OIDC claims for the authenticated user (Bearer token required).

**Acceptance criteria:**
- [x] `GET /userinfo` with valid Bearer token returns JSON claims
- [x] Claims include `sub`, `scope`, `name`, `email` (if `email` scope requested)
- [x] Returns 401 if no or invalid token
- [x] Claims consistent with ID token issued for same auth

**Verification:** `curl -H "Authorization: Bearer <token>" http://localhost:8080/userinfo` → JSON claims.

**Dependencies:** Task 11

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/UserInfoController.java`

**Estimated scope:** S

---

### Checkpoint: Phase 3 ✅

- [x] Discovery + JWKS endpoints work (Task 10)
- [x] ID token issued and validatable (Task 11)
- [x] `/userinfo` returns correct claims (Task 12)
- [x] `./mvnw test -pl backend/oauth-oidc` passes
- [x] Human reviews before Phase 4

---

### Phase 4: Token Lifecycle

---

#### Task 13: Refresh Token Rotation

**Description:** `POST /token` with `grant_type=refresh_token`. Issues new access + refresh token. Atomically invalidates the used refresh token. Old refresh token cannot be reused.

**Acceptance criteria:**
- [x] Valid refresh token → new access token + new refresh token issued
- [x] Old refresh token atomically revoked in PostgreSQL
- [x] Reusing old refresh token → 400 `invalid_grant`; both tokens in that exchange family revoked
- [x] Refresh token bound to same `client_id` that originally issued it

**Verification:** Two sequential refresh calls — first succeeds, second returns `invalid_grant`.

**Dependencies:** Task 7

**Files:**
- `backend/oauth-oidc/src/main/java/.../service/TokenService.java` (handleRefreshTokenGrant method)

**Estimated scope:** M

---

#### Task 14: /oauth2/introspect + /oauth2/revoke Endpoints

**Description:** `POST /oauth2/introspect` (RFC 7662) and `POST /oauth2/revoke` (RFC 7009) endpoints.

**Acceptance criteria:**
- [x] `/oauth2/introspect` with valid token → `{"active": true, "sub": ..., "scope": ..., "exp": ...}`
- [x] `/oauth2/introspect` with revoked/expired token → `{"active": false}`
- [x] `/oauth2/revoke` immediately marks token revoked in DB (returns 200 always per RFC 7009 §2.2)
- [x] Introspection works for both access and refresh tokens (token_type hint)

**Verification:** Token introspection against: (a) valid token, (b) revoked token, (c) expired token.

**Dependencies:** Task 7

**Files:**
- `backend/oauth-oidc/src/main/java/.../controller/IntrospectionController.java`
- `backend/oauth-oidc/src/main/java/.../controller/RevocationController.java`

**Estimated scope:** S

---

### Checkpoint: Phase 4 ✅

- [x] Refresh rotation works (Task 13) — learning doc written; code implemented in `TokenService.handleRefreshTokenGrant()`
- [x] Introspect + revoke work (Task 14) — learning doc written; `IntrospectionController` + `RevocationController` implemented
- [x] SC-03, SC-04 from SPEC.md satisfied
- [x] Learning site chapters written (`frontend/app/docs/04-Token-Lifecycle/`)
- [ ] Human reviews before Phase 5

---

### Learning Site: Phase 3 Chapters

**Location:** `frontend/app/docs/03-OIDC/`
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

| Chapter | Source |
|---------|--------|
| `03-OIDC/00-Overview` | Phase 3 overview, key design decisions, test coverage |
| `03-OIDC/01-Discovery` | RFC 8414, issuer, jwks_uri, claims_supported |
| `03-OIDC/02-JWKS` | kid derivation, key rotation, PKCS12 keystore |
| `03-OIDC/03-ID-Token` | JWT structure, RS256 vs HS256, nonce, verification checklist |
| `03-OIDC/04-UserInfo` | OIDC Core §5.3, scope-driven claims, full flow diagram |

---

### Learning Site: Phase 4 Chapters

**Location:** `frontend/app/docs/04-Token-Lifecycle/`

| Chapter | Source |
|---------|--------|
| `04-Token-Lifecycle/00-Overview` | Phase 4 overview, module changes, flows summary |
| `04-Token-Lifecycle/01-Refresh-Rotation` | RFC 6749 §6, atomic rotation, reuse detection, blast radius |
| `04-Token-Lifecycle/02-Introspection` | RFC 7662, active/inactive responses, active token fields |
| `04-Token-Lifecycle/03-Revocation` | RFC 7009, immediate invalidation, 200 always returned |

---

### Phase 5: Admin UI

---

#### Task 15: React + Vite + MUI Scaffold

**Description:** Frontend workspace with React 19, TypeScript, Vite, MUI. Auth SDK (`src/lib/auth.ts`) that wraps all backend calls.

**Acceptance criteria:**
- [x] `npm run dev` starts Vite dev server on port 5173
- [x] `npm run build` produces production build with no errors
- [x] MUI theme configured with custom palette (violet/cyan dark theme)
- [x] Auth SDK has typed methods: `login(username, password)`, `exchangeCode(code, verifier)`, `refreshToken()`, `introspectToken()`, `revokeToken()`

**Verification:** `npm run build` succeeds; Auth SDK types compile.

**Dependencies:** Task 7 (backend must be runnable)

**Files:**
- `frontend/admin/package.json`
- `frontend/admin/vite.config.ts`
- `frontend/admin/src/main.tsx`
- `frontend/admin/src/lib/auth.ts`
- `frontend/admin/src/theme/index.ts`
- `frontend/admin/src/components/Layout.tsx`
- `frontend/admin/src/components/ProtectedRoute.tsx`

**Estimated scope:** M

---

#### Task 16: Login + Consent Pages

**Description:** Login page (username/password form). Consent is implicit (auto-approved) for Phase 5 — full scope approval UI in future.

**Acceptance criteria:**
- [x] Login page posts to `/login` endpoint; stores `login_token` in sessionStorage
- [x] Login page redirects to `/authorize` with `login_token` param (PKCE flow)
- [x] Protected routes redirect to login if unauthenticated
- [x] Error messages displayed for failed login

**Verification:** Manual browser test: start backend + frontend, complete login flow, verify tokens stored.

**Dependencies:** Task 15

**Files:**
- `frontend/admin/src/pages/LoginPage.tsx`
- `backend/oauth-oidc/src/main/java/.../controller/LoginController.java`
- `backend/oauth-oidc/src/main/java/.../controller/AuthorizeController.java` (updated for login_token)

**Estimated scope:** M

---

#### Task 17: Admin CRUD Pages

**Description:** Client list, Audit log viewer with MUI DataTables. User/Group pages are placeholders (full CRUD in Phase 6 SCIM).

**Acceptance criteria:**
- [x] Client list: columns for client_id, grant_types, scopes, redirect_uris via `/admin/clients`
- [x] Audit log: columns for timestamp, event_type, actor, client_id, ip_address; filterable via `/admin/audit`
- [x] User list: placeholder (Phase 6 SCIM)
- [x] All admin endpoints protected by Bearer token auth

**Verification:** Admin pages load data from live backend.

**Dependencies:** Task 15

**Files:**
- `frontend/admin/src/pages/UsersPage.tsx`
- `frontend/admin/src/pages/ClientsPage.tsx`
- `frontend/admin/src/pages/AuditPage.tsx`
- `frontend/admin/src/pages/GroupsPage.tsx`
- `backend/oauth-oidc/src/main/java/.../controller/AdminController.java`

**Estimated scope:** L (break down: UsersPage=S, ClientsPage=S, AuditPage=S)

---

### Checkpoint: Phase 5 ✅

- [x] Login + consent flow works end-to-end (login page → /login → /authorize → /callback → tokens)
- [x] Admin pages render and are functional (ClientsPage, AuditPage from real DB; UsersPage/GroupsPage placeholder)
- [x] `./mvnw test` backend passes (44 tests); `npm run build` frontend passes
- [x] Human reviews before Phase 6

---

### Learning Site: Phase 5 Chapters

**Location:** `frontend/app/docs/05-Admin-UI/`

| Chapter | Source |
|---------|--------|
| `05-Admin-UI/00-Overview` | Phase 5 overview, architecture, test accounts |
| `05-Admin-UI/01-Login-Flow` | Login token, Redis session, PKCE redirect flow |
| `05-Admin-UI/02-Admin-API` | /admin/clients, /admin/audit, /admin/users endpoints |

---

### Phase 6: SCIM 2.0

---

#### Task 18: SCIM /Users CRUD

**Description:** Full SCIM 2.0 `/Users` implementation. `POST /scim/v2/Users` (create), `GET /scim/v2/Users` (list with `?filter=`), `GET /scim/v2/Users/{id}` (read), `PUT /scim/v2/Users/{id}` (replace), `DELETE /scim/v2/Users/{id}` (delete). All RFC 7644 compliant.

**Acceptance criteria:**
- [x] `POST /scim/v2/Users` creates user, returns 201 with `id`, `meta`
- [x] `GET /scim/v2/Users?filter=userName eq "john"` returns filtered results
- [x] `GET /scim/v2/Users/{id}` returns full user representation
- [x] `PUT /scim/v2/Users/{id}` replaces user (mover flow)
- [x] `DELETE /scim/v2/Users/{id}` hard-deletes (leaver flow)
- [x] Error responses return SCIM `Error` schema (RFC 7643)

**Verification:** curl tests for each HTTP method; RFC 7644 compliance verified.

**Dependencies:** Task 4

**Files:**
- `backend/scim/src/main/java/com/iam/scim/controller/ScimUserController.java`
- `backend/scim/src/main/java/com/iam/scim/service/ScimUserService.java`
- `backend/scim/src/main/java/com/iam/scim/dto/ScimUserDto.java`
- `backend/scim/src/main/java/com/iam/scim/dto/ScimError.java`
- `backend/scim/src/main/java/com/iam/scim/dto/ScimListResponse.java`
- `backend/scim/src/main/java/com/iam/scim/repository/ScimUserRepository.java`
- `backend/scim/src/test/java/com/iam/scim/ScimUserServiceTest.java`

**Estimated scope:** M

---

#### Task 19: SCIM /Groups CRUD + Joiner/Mover/Leaver E2E

**Description:** SCIM `/Groups` endpoint + membership management. Joiner/mover/leaver flows wired to token revocation.

**Acceptance criteria:**
- [x] `POST /scim/v2/Groups` creates group
- [x] `GET /scim/v2/Groups` lists groups with optional filter
- [x] `GET /scim/v2/Groups/{id}` returns group with members
- [x] `PATCH /scim/v2/Groups/{id}` adds/removes members (RFC 7644 Section 4.3)
- [x] `DELETE /scim/v2/Groups/{id}` deletes group

**Note:** JML lifecycle token revocation implemented in Phase 7 Task 23 — `ScimUserService.deleteUser()` calls `TokenService.revokeAllTokensForUser()` before hard-delete.

**Verification:** curl tests for each HTTP method.

**Dependencies:** Task 18

**Files:**
- `backend/scim/src/main/java/com/iam/scim/controller/ScimGroupController.java`
- `backend/scim/src/main/java/com/iam/scim/service/ScimGroupService.java`
- `backend/scim/src/main/java/com/iam/scim/dto/ScimGroupDto.java`
- `backend/scim/src/main/java/com/iam/scim/repository/ScimGroupRepository.java`

**Estimated scope:** M

---

### Checkpoint: Phase 6 ✅

- [x] SC-08 from SPEC.md satisfied
- [x] `./mvnw test -pl backend/scim` passes
- [x] `./mvnw test -pl backend/auth-core` passes
- [x] API testing via curl confirmed all endpoints
- [x] Postman collection updated for Phase 6 SCIM
- [x] Human reviews before Phase 7

---

### Phase 7: SAML 2.0

---

#### Task 20: SAML SP Metadata + AuthnRequest

**Description:** SAML SP generates metadata (XML) and sends `AuthnRequest` to IdP. Supports SP-initiated SSO.

**Acceptance criteria:**
- [x] `GET /saml/metadata` returns SP metadata XML (signed)
- [x] `GET /saml/initiate?client_id=CLIENT&redirect_uri=URI` builds `AuthnRequest`, encodes as SAMLRequest, redirects to IdP SSO URL
- [x] `AuthnRequest` is signed with SP private key
- [x] Request ID stored in in-memory map for replay protection (5-minute TTL)

**Verification:** `curl http://localhost:8080/saml/metadata` → valid XML. `curl -v "http://localhost:8080/saml/initiate?client_id=test&redirect_uri=https://app.example.com/cb"` → redirect to IdP.

**Dependencies:** Task 4

**Files:**
- `backend/saml-federation/src/main/java/.../controller/SamlController.java`
- `backend/saml-federation/src/main/java/.../service/SamlMetadataService.java`
- `backend/saml-federation/src/main/java/.../service/SamlAuthnRequestBuilder.java`

**Estimated scope:** M

---

#### Task 21: ACS Endpoint + Assertion Validation

**Description:** `POST /saml/acs` receives `SAMLResponse` from IdP. Validates: signature, `AssertionConsumerServiceURL`, `Destination`, `NotBefore`/`NotOnOrAfter`, audience restriction, in-response-to validation.

**Acceptance criteria:**
- [x] Signature validation passes with IdP certificate
- [x] Rejects assertion with expired `NotOnOrAfter`
- [x] Rejects assertion with wrong audience
- [x] Rejects assertion not in-response-to a stored request
- [x] Extracts `NameID`, `attributes`, `session index` from assertion

**Verification:** Integration test against Keycloak IdP or simulated assertion.

**Dependencies:** Task 20

**Files:**
- `backend/saml-federation/src/main/java/.../controller/SamlAcsController.java`
- `backend/saml-federation/src/main/java/.../service/SamlAssertionValidator.java`

**Estimated scope:** M

---

#### Task 22: SAML → OIDC Claim Bridge

**Description:** After SAML assertion validated, maps SAML attributes to OIDC claims and issues same tokens as OAuth flow (access token, ID token).

**Acceptance criteria:**
- [x] SAML `NameID` → `sub` claim
- [x] SAML attributes mapped via configurable claim mapping table
- [x] Issues access token + ID token (same as OAuth flow) after successful SAML auth
- [x] Redirects to `redirect_uri` with tokens or token fragment

**Verification:** Full SAML SSO flow → tokens issued, tokens validatable via `/introspect`.

**Dependencies:** Tasks 11, 21

**Files:**
- `backend/saml-federation/src/main/java/.../service/ClaimBridgeService.java`
- `backend/saml-federation/src/main/java/.../service/SamlToOidcTokenService.java`

**Estimated scope:** S

---

#### Task 23: SCIM JML Lifecycle → Token Revocation

**Description:** JML (Joiner/Mover/Leaver) lifecycle hooks wired to token revocation. When a SCIM user is deleted (leaver), all their active tokens are revoked immediately via `TokenService.revokeAllTokensForUser(subject)`.

**Acceptance criteria:**
- [x] `DELETE /scim/v2/Users/{id}` (leaver): calls `TokenService.revokeAllTokensForUser(userName)` before hard-delete
- [x] `POST /scim/v2/Users` (joiner): logs JML joiner event
- [x] `ScimUserService` injects `TokenService`; `scim/pom.xml` adds `oauth-oidc` dependency

**Verification:** Delete a SCIM user that has active tokens; introspect tokens → `active: false`.

**Files:**
- `backend/scim/src/main/java/.../service/ScimUserService.java`
- `backend/oauth-oidc/src/main/java/.../service/TokenService.java` (new `revokeAllTokensForUser` method)
- `backend/scim/pom.xml` (added oauth-oidc dependency)

**Estimated scope:** S

---

### Checkpoint: Phase 7

- [x] Full SAML SP-initiated SSO flow works end-to-end
- [x] Tokens issued after SAML auth are validatable by demo-resource
- [x] Human reviews before Phase 8

---

### Learning Site: Phase 7 Chapters

**Location:** `frontend/app/docs/07-SAML/`
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

| Chapter | Source |
|---------|--------|
| `07-SAML/00-Overview` | Phase 7 overview, SP-initiated vs IdP-initiated SSO |
| `07-SAML/01-SP-Metadata` | SAML 2.0 §2, EntityDescriptor, SPSSODescriptor, KeyDescriptor |
| `07-SAML/02-AuthnRequest` | SAML 2.0 §3.4, AuthnRequest building, signing, redirect binding |
| `07-SAML/03-ACS-Assertion` | SAML 2.0 §3.2, AssertionConsumerService, signature validation |
| `07-SAML/04-SAML-OIDC-Bridge` | Claim mapping, NameID → sub, token issuance post-SAML |
| `07-SAML/05-JML-Lifecycle` | Joiner/Mover/Leaver flows, SCIM → token revocation integration |

---

### Phase 7 Postman Collection

**File:** `postman/iam-protocol-engine.json`

Phase 7 SAML collection includes:
- `GET /saml/metadata` — SP metadata XML
- `GET /saml/initiate` — AuthnRequest redirect builder
- `POST /saml/acs` — Assertion consumer (simulated or Keycloak)
- `POST /saml/bridge/token` — SAML → OIDC token issuance
- Joiner/Mover/Leaver token revocation sequences (Tasks 22, 23)

---

### Phase 8: SAML 2.0 — Documentation + Postman

*Phase 7 implementation complete. Phase 8 is the documentation and Postman integration pass for SAML 2.0 before moving to Modern Auth.*

**Note:** This is not a re-implementation phase. All SAML code is in Phase 7. Phase 8 writes the learning site chapters and updates the Postman collection with SAML sequences (SP metadata, AuthnRequest redirect, ACS assertion validation, SAML→OIDC bridge, JML lifecycle).

**Acceptance criteria:**
- [ ] All Phase 7 code compiles and module tests pass
- [ ] `frontend/app/docs/07-SAML/` chapters written and navigable
- [ ] Postman collection includes SAML 2.0 sequences with variable substitution
- [ ] `doc/codechange/06_P7-CODE-CHANGE-SUMMARY.md` written

**Verification:** Learning site renders correctly; Postman collection passes schema validation.

**Dependencies:** Phase 7 Tasks 20-23

---

### Phase 8: Modern Auth (TOTP + WebAuthn + Device Flow)

---

#### Task 24: TOTP MFA

**Description:** TOTP enrollment (`/mfa/totp/setup`) and verification (`/mfa/totp/verify`). Uses `dev.samstevens.totp`.

**Acceptance criteria:**
- [x] `POST /mfa/totp/setup` generates secret, returns provisioning URI + QR code payload
- [x] User verifies with TOTP code → `TotpCredential.verified=true`
- [x] Auth flow can require TOTP step-up after password verification
- [x] `POST /mfa/totp/verify` validates code; returns success/failure

**Verification:** Set up TOTP → scan QR with authenticator → verify with 6-digit code → success.

**Dependencies:** Task 16

**Files:**
- `backend/mfa/src/main/java/.../controller/TotpController.java`
- `backend/mfa/src/main/java/.../service/TotpService.java`
- `backend/auth-core/src/main/resources/db/migration/V3__add_totp_credential.sql`

**Estimated scope:** M

---

#### Task 25: WebAuthn Registration + Authentication

**Description:** WebAuthn credential registration and assertion verification using `webauthn4j-core`.

**Acceptance criteria:**
- [x] `POST /webauthn/register/begin` returns challenge + registration options
- [x] `POST /webauthn/register/complete` stores credential; `WebAuthnCredential` created
- [x] `POST /webauthn/authenticate/begin` returns assertion options
- [x] `POST /webauthn/authenticate/complete` verifies assertion; login proceeds
- [x] `sign_count` incremented on each authentication

**Verification:** Register credential via browser → authenticate via browser → assertion verified.

**Dependencies:** Task 16

**Files:**
- `backend/mfa/src/main/java/.../controller/WebAuthnController.java`
- `backend/mfa/src/main/java/.../service/WebAuthnService.java`
- `backend/auth-core/src/main/resources/db/migration/V4__add_webauthn_credential.sql`

**Estimated scope:** L

---

#### Task 26: Device Authorization Grant (RFC 8628)

**Description:** Device flow: `POST /device_authorization` starts flow, `GET /device` shows user code approval page, `POST /oauth2/token` with `grant_type=urn:ietf:params:oauth:grant-type:device_code` polls for approval.

**Acceptance criteria:**
- [x] `POST /device_authorization` returns `device_code` + `user_code` + `verification_uri`
- [x] `GET /device?user_code=X` shows approval page
- [x] Device code stored in PostgreSQL with 10-minute TTL
- [x] Polling `/oauth2/token` returns `authorization_pending` until approved, then issues tokens
- [x] Expired device codes return `token_expired` error

**Verification:** Device flow E2E — start flow → approve in browser → poll → tokens issued.

**Dependencies:** Task 4

**Files:**
- `backend/device-flow/src/main/java/.../controller/DeviceAuthorizationController.java`
- `backend/device-flow/src/main/java/.../service/DeviceFlowService.java`
- `backend/oauth-oidc/src/main/java/.../controller/TokenController.java` (device_code grant)
- `backend/auth-core/src/main/resources/db/migration/V5__add_device_code.sql`

**Estimated scope:** M

---

### Checkpoint: Phase 8

- [x] TOTP enrollment + verification works
- [x] WebAuthn registration + authentication works
- [x] Device flow completes end-to-end
- [ ] Human reviews before Phase 9

---

### Phase 9: Demo Hardening

---

#### Task 27: End-to-End Demo Script

**Description:** Bash script that runs through all major flows: OAuth, OIDC, SCIM, SAML, Device, MFA. Each step prints expected output.

**Acceptance criteria:**
- [x] Script idempotent (can be run on clean `up -d`)
- [x] Each flow has curl command + expected output comment
- [x] All flows complete without manual intervention
- [x] Script output is copy-pasteable for demo

**Verification:** Script runs end-to-end on clean environment.

**Dependencies:** Tasks 2-26

**Files:**
- `scripts/demo-e2e.sh`

**Estimated scope:** S

---

#### Task 28: Architecture README + Sequence Diagrams

**Description:** `README.md` with architecture overview, tech stack, module responsibilities, and Mermaid sequence diagrams for each major flow.

**Acceptance criteria:**
- [x] README includes architecture diagram (mermaid)
- [x] Each protocol flow has a sequence diagram
- [x] Setup instructions: how to start, how to run demo
- [x] All module responsibilities clearly documented

**Verification:** README renders correctly on GitHub (mermaid diagrams).

**Dependencies:** Tasks 2-26

**Files:**
- `README.md`

**Estimated scope:** S

---

### Checkpoint: Phase 9

- [x] Demo script runs clean
- [x] README is complete and accurate
- [x] SC-01 through SC-15 all satisfied (from SPEC.md)
- [x] Human final review — project complete

---

### Learning Site: Phase 9 Chapters

**Location:** `frontend/app/docs/09-Demo-Hardening/`
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

| Chapter | Source |
|---------|--------|
| `09-Demo-Hardening/00-Overview` | Phase 9 overview, demo readiness |
| `09-Demo-Hardening/01-Demo-Script` | `scripts/demo-e2e.sh` walkthrough |
| `09-Demo-Hardening/02-Architecture` | README architecture, module map, sequence diagrams |

---

### Phase 10: Learning Site Refresh (AgentWay-alike UX)

**Goal:** Transform the learning site into a premium docs-based course platform inspired by AgentWay Learn's product shell, information architecture, lesson progression, and calm reading experience, while remaining original in branding, copy, and visual execution.

**Reference brief:** `UI_UX.md`

**Planning assumption:** Phase 10 is a redesign of the existing Docusaurus learning site in `frontend/app/`. A full migration to Next.js + Tailwind is out of scope unless explicitly approved as a separate architectural phase.

**Execution branch:** `phase-10-develop`

**Implementation approach:**
- Preserve the current Docusaurus docs content and route structure wherever practical
- Restyle the site through `src/theme/` overrides, reusable React components, and `src/css/custom.css`
- Treat the sidebar, lesson shell, header, and TOC as a unified learning product shell rather than isolated page tweaks
- Use CSS variables for tokens and keep the design system framework-agnostic even though Phase 10 stays on Docusaurus

**Non-goals:**
- Do not copy AgentWay branding, logo, illustrations, wording, or exact visual design
- Do not turn the learning site into a marketing landing page
- Do not replace documentation depth with decorative UI
- Do not hide a frontend platform migration inside a visual refresh

---

#### Task 29: Information Architecture + Curriculum Navigation

**Description:** Rework the learning site structure so it behaves like a guided curriculum platform with visible track progression, stable lesson IDs, clearer chapter sequencing, and a curriculum-first navigation model.

**Acceptance criteria:**
- [ ] Learning home page presents tracks as a curated curriculum, not a generic docs index
- [ ] Information architecture supports 4 core screen types: learning home, track overview, lesson detail, and mobile drawer state
- [ ] Left sidebar is reorganized into named tracks and chapters with stable lesson identifiers and status affordances
- [ ] Lesson order is obvious without requiring users to infer sequence from URLs
- [ ] Advanced or optional material is clearly separated into dedicated sections
- [ ] Lesson pages expose clear progression with previous/next navigation and optional back-to-track links
- [ ] Existing IAM protocol content remains reachable and logically grouped

**Verification:** Manual walkthrough on desktop and mobile shows a coherent course-style navigation model across homepage, track page, and lesson page.

**Dependencies:** Phase 9

**Files:**
- `frontend/app/docusaurus.config.ts`
- `frontend/app/sidebars.ts`
- `frontend/app/docs/**`
- `frontend/app/src/pages/**`
- `frontend/app/src/components/**`
- `frontend/app/src/theme/**`

**Estimated scope:** M

---

#### Task 30: Premium Visual System + Layout Shell

**Description:** Implement the premium editorial visual system for the learning site: sticky top navbar, sticky syllabus sidebar, optional right-side TOC, light-first tokens, typography system, and reusable layout primitives.

**Acceptance criteria:**
- [ ] Layout follows a 3-part learning shell: sticky top navbar, sticky left syllabus sidebar, centered reading area, optional right TOC
- [ ] Sticky global header is 64px tall and includes brand area, top-level navigation, search trigger, language switcher, and sign-in/account CTA
- [ ] Sticky left syllabus sidebar sits below the header, is independently scrollable, and feels curated rather than file-tree-like
- [ ] Optional right-side TOC is visible on large screens only and stays visually quiet
- [ ] Light-first design tokens from `UI_UX.md` are implemented with CSS variables
- [ ] Typography uses heading, body, and mono roles that produce a premium technical editorial reading experience
- [ ] Spacing follows the defined 4/8/12/16/24/32/48/64 rhythm with subtle radius and thin borders
- [ ] Visual design is original and production-ready, not a clone of AgentWay Learn

**Verification:** `cd frontend/app && npm run build` succeeds; manual review confirms sticky regions do not overlap content and the site reads cleanly on desktop/tablet/mobile.

**Dependencies:** Task 29

**Files:**
- `frontend/app/src/css/custom.css`
- `frontend/app/src/theme/**`
- `frontend/app/src/components/**`
- `frontend/app/src/pages/**`
- `frontend/app/static/**`

**Estimated scope:** L

---

#### Task 31: Lesson Experience + Progress UX

**Description:** Upgrade lesson pages into a course-quality reading experience with stronger metadata, info capsules, callouts, TOC support, guided lesson progression, and a non-blocking progress CTA.

**Acceptance criteria:**
- [ ] Lesson pages include a strong H1, one-sentence summary, metadata row, and compact info strip
- [ ] Metadata row surfaces lesson ID, duration, and lesson type
- [ ] Info strip supports fields such as SDK Focus, Prerequisites, and Related Exercise
- [ ] Content styling supports paragraphs, tables, code blocks, diagrams, and subtle callouts in a documentation-first reading experience
- [ ] Previous/next lesson cards show lesson ID and title and clearly reinforce guided progression
- [ ] "Sign in to save progress" or equivalent panel is present, helpful, and non-blocking
- [ ] Accessibility expectations met: keyboard navigation, visible focus states, reduced-motion support, and non-color-only status indicators

**Verification:** Manual review of at least 3 representative lessons confirms readability, progression UX, and accessibility affordances.

**Dependencies:** Tasks 29, 30

**Files:**
- `frontend/app/src/pages/**`
- `frontend/app/src/components/**`
- `frontend/app/src/css/custom.css`
- `frontend/app/docs/**`

**Estimated scope:** M

---

#### Task 32: Content Migration + Production Polish

**Description:** Adapt current IAM learning content into the new layout, create track and overview surfaces where needed, and complete responsive, accessibility, and production-quality polish.

**Acceptance criteria:**
- [ ] Existing protocol chapters are mapped into track-based navigation without losing coverage
- [ ] Home page and track overview page present the curriculum clearly for new visitors
- [ ] Mobile course drawer works without horizontal scrolling and preserves orientation
- [ ] Sidebar supports clear default, hover, active, completed, and locked or preview states
- [ ] Badge system supports optional, exercise, and capstone states with restrained styling
- [ ] Empty states and search-entry affordances feel intentional, not placeholder-level
- [ ] QA is completed at 320px, 768px, 1024px, and 1440px widths
- [ ] Final build is ready for GitHub Pages deployment

**Verification:** `cd frontend/app && npm run build` succeeds and key pages render correctly in responsive browser checks.

**Dependencies:** Tasks 29, 30, 31

**Files:**
- `frontend/app/src/pages/index.tsx`
- `frontend/app/src/pages/**`
- `frontend/app/docs/**`
- `frontend/app/static/**`
- `frontend/app/src/theme/**`

**Estimated scope:** M

---

### Phase 10 Suggested Task Branches

- `phase-10-task-29-ia-navigation`
- `phase-10-task-30-learning-shell`
- `phase-10-task-31-lesson-experience`
- `phase-10-task-32-content-polish`

---

### Checkpoint: Phase 10

- [ ] Learning site presents a calm, premium, course-like UX
- [ ] Navigation model supports home, track overview, lesson detail, and mobile drawer states
- [ ] Global header, syllabus sidebar, and lesson navigation all reinforce curriculum orientation
- [ ] Existing IAM content is preserved and easier to navigate
- [ ] Responsive and accessibility checks completed
- [ ] Human design review before release

---

### Learning Site: Phase 10 Chapters

**Location:** `frontend/app/`
**Live at:** `https://hoimingkenny.github.io/iam-protocol-engine/`

| Chapter | Source |
|---------|--------|
| `Learning Home` | `UI_UX.md`, curriculum structure, course platform framing |
| `Track Overview` | Reworked sidebar taxonomy and track progression |
| `Lesson Detail Experience` | Metadata bar, info capsules, TOC, previous/next flow |
| `Design System` | Tokens, typography, spacing, sticky layout shell |
| `Mobile Navigation` | Drawer behavior, responsive reading layout |
| `Component System` | Header, sidebar, badges, progress CTA, lesson navigation, TOC |

---

### Phase 11: Fumadocs Interactive Docs Studio

**Goal:** Add a second documentation experience powered by Next.js + Fumadocs for interactive authorization flow walkthroughs, protocol reference, and personal study tooling, while preserving the existing Docusaurus site as the broader long-form documentation base.

**Planning assumption:** Phase 11 introduces a new app in `frontend/fumadocs/` instead of migrating `frontend/app/`. Docusaurus remains live and maintained. The new Fumadocs site focuses on interactive protocol visualizations, API-backed reference surfaces, and study-friendly technical exploration that benefits from Next.js server capabilities.

**Reference inputs:** Fumadocs official docs, existing IAM protocol content in `frontend/app/docs/`, backend modules under `backend/`

**Execution branch:** `phase-11-develop`

**Implementation approach:**
- Keep `frontend/app/` as the current Docusaurus documentation site with no breaking route or content removal
- Add `frontend/fumadocs/` as a separate Next.js app using Fumadocs for interactive docs and API reference
- Treat Spring Boot as the protocol engine and source of truth; Next.js is a presentation and orchestration layer only
- Reuse existing Markdown and architecture knowledge where practical, but adapt content into interaction-first reference experiences instead of mirroring pages one-for-one
- Prefer static generation for narrative/reference pages and server routes only where interactivity, proxying, or secure demo glue is actually needed

**Non-goals:**
- Do not replace the Java backend with Next.js API logic
- Do not silently migrate or deprecate the Docusaurus site during this phase
- Do not create a second copy of every existing doc without a distinct interactive or reference purpose
- Do not change token lifetimes, grant types, persistence design, or protocol security constraints as part of frontend work

---

#### Task 33: Fumadocs App Scaffold + Platform Decisions

**Description:** Scaffold a dedicated Next.js + Fumadocs app in `frontend/fumadocs/`, define workspace conventions, and document how it coexists with the existing Docusaurus site.

**Acceptance criteria:**
- [ ] `frontend/fumadocs/` exists as an isolated Next.js + Fumadocs app
- [ ] Node and package manager requirements are documented for the new app
- [ ] Base routing is defined so the app can host docs and interactive flow pages without conflicting with Docusaurus
- [ ] Shared repository conventions are documented: where content lives, how to run locally, and how to build
- [ ] Coexistence strategy between `frontend/app/` and `frontend/fumadocs/` is written down clearly

**Verification:** `cd frontend/fumadocs && npm install && npm run build` succeeds on a clean checkout.

**Dependencies:** Phase 10

**Files:**
- `frontend/fumadocs/package.json`
- `frontend/fumadocs/next.config.*`
- `frontend/fumadocs/app/**`
- `frontend/fumadocs/content/**`
- `frontend/fumadocs/README.md`
- `README.md`

**Estimated scope:** M

---

#### Task 34: Interactive OAuth/OIDC Flow Explorer

**Description:** Build the first interactive flow explorer in Fumadocs for OAuth 2.0 Authorization Code + PKCE and OIDC login, with step-by-step state visualization, request/response examples, and callback-aware demo pages.

**Acceptance criteria:**
- [ ] Interactive flow pages explain the major actors, requests, redirects, and token issuance steps
- [ ] Users can walk through Authorization Code + PKCE from browser UI to callback result
- [ ] OIDC additions such as discovery, ID token, and UserInfo are represented in the flow experience
- [ ] UI exposes request parameters, returned payloads, and state transitions in a study-friendly, inspectable way
- [ ] Demo pages can safely read from configured backend endpoints without weakening protocol constraints
- [ ] Callback and token display surfaces clearly distinguish study/demo UX from production guidance

**Verification:** Local walkthrough completes authorize redirect, callback handling, and token/result inspection against the existing Spring backend.

**Dependencies:** Task 33

**Files:**
- `frontend/fumadocs/app/**`
- `frontend/fumadocs/components/**`
- `frontend/fumadocs/lib/**`
- `frontend/fumadocs/content/**`

**Estimated scope:** L

---

#### Task 35: API Reference + OpenAPI Integration

**Description:** Use Fumadocs OpenAPI support to create a richer API reference for selected IAM endpoints, with interactive request exploration and concise protocol context.

**Acceptance criteria:**
- [ ] At least one high-value API area is exposed through Fumadocs OpenAPI pages
- [ ] API reference includes endpoint purpose, parameters, example payloads, and response semantics
- [ ] Interactive API exploration is constrained to approved local/demo targets only
- [ ] Generated API pages are integrated into the overall docs navigation rather than left as a disconnected sandbox
- [ ] OpenAPI reference complements narrative docs instead of replacing protocol explanations

**Verification:** Fumadocs build succeeds and rendered API pages load correctly in the local app.

**Dependencies:** Tasks 33, 34

**Files:**
- `frontend/fumadocs/lib/openapi.*`
- `frontend/fumadocs/app/**`
- `frontend/fumadocs/content/**`
- `frontend/fumadocs/components/**`

**Estimated scope:** M

---

#### Task 36: Dual-Site Navigation + Deployment Strategy

**Description:** Finalize how Docusaurus and Fumadocs link to each other, document hosting strategy, and ensure contributors can develop and deploy both sites without confusion.

**Acceptance criteria:**
- [ ] Primary navigation makes the distinction between "Documentation" and "Interactive Docs" obvious
- [ ] Both sites link to each other intentionally without route ambiguity
- [ ] Deployment strategy is documented for local dev, preview, and production hosting
- [ ] Contributor documentation explains when to add content to Docusaurus versus Fumadocs
- [ ] The dual-site model feels deliberate, not transitional or duplicated

**Verification:** Manual review confirms both sites build, link correctly, and present a coherent documentation strategy.

**Dependencies:** Tasks 33, 34, 35

**Files:**
- `README.md`
- `frontend/app/**`
- `frontend/fumadocs/**`
- `doc/**`

**Estimated scope:** M

---

### Phase 11 Suggested Task Branches

- `phase-11-task-33-fumadocs-scaffold`
- `phase-11-task-34-interactive-flow-lab`
- `phase-11-task-35-openapi-reference`
- `phase-11-task-36-dual-site-deployment`

---

### Checkpoint: Phase 11

- [ ] Docusaurus remains intact as the long-form documentation site
- [ ] A new Fumadocs app exists in `frontend/fumadocs/`
- [ ] Interactive OAuth/OIDC flow explorer works against the existing backend
- [ ] API reference experience proves the value of Fumadocs beyond static content
- [ ] Dual-site information architecture and deployment guidance are documented
- [ ] Human architectural review confirms the two-site model is worth maintaining

---

### Documentation: Phase 11 Surfaces

**Location:** `frontend/fumadocs/`
**Role:** Interactive docs studio and API explorer alongside the Docusaurus documentation site

| Surface | Source |
|---------|--------|
| `Interactive Flow Home` | Phase 11 overview, interactive-docs positioning |
| `OAuth 2.0 PKCE Explorer` | Existing Phase 2 docs and live backend behavior |
| `OIDC Login Explorer` | Existing Phase 3 docs and discovery/token payloads |
| `API Reference` | OpenAPI-backed endpoint reference for selected flows |
| `Dual-Site Navigation` | Cross-links between Docusaurus docs and Fumadocs interactive docs |

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| SAML assertion validation edge cases | High | Test against real Keycloak IdP early; don't skip signature validation |
| SCIM schema complexity | Medium | Stick to core User/Group schemas; implement extensions later |
| Refresh token race conditions | Medium | Redis WATCH/MULTI for atomic rotation |
| Frontend scope creep | Medium | Keep UI to admin + playground only; no customer-facing UX |
| Key rotation breaking live tokens | Medium | Old keys remain valid in JWKS until all tokens using them expire |

---

## Parallelization Opportunities

| Tasks | Can run in parallel with |
|-------|--------------------------|
| Tasks 6-9 (OAuth core) | Tasks 15-17 (Frontend scaffold) — no dependency between them |
| Task 18 (SCIM Users) | Task 20 (SAML metadata) — no shared code |
| Task 24 (TOTP) | Task 25 (WebAuthn) — separate endpoints, no shared state |
| Task 26 (Device Flow) | Task 18 — `device-flow` module is isolated |

---

## Open Questions

1. **SC-15 — Frontend auth storage:** sessionStorage (Bearer token in memory) or httpOnly cookie? → *sessionStorage for Bearer; httpOnly cookie adds complexity without demo value*
2. **SCIM bulk vs. individual:** Implement SCIM bulk endpoint? → *No — single-user CRUD is sufficient for portfolio demo*
3. **SAML/OIDC token delivery:** Redirect with fragment (`#`) or query (`?`)? → *query params for simplicity; fragment is OIDC convention but SAML uses query*
