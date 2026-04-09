# Phase 2 Code Change Summary

**Branch:** `phase-3` (HEAD)
**Commits:** 12 (from `c6673c1` to `94caa94`)
**Lines changed:** ~+3,095 across 46 files

---

## Phase 2 — OAuth 2.0 Core

### Task 6 — `/authorize` Endpoint (`c6673c1`)

RFC 6749 §4.1 authorization endpoint with PKCE (RFC 7636) support.

**Files created:**

| File | Description |
|------|-------------|
| `oauth-oidc/src/main/java/.../controller/AuthorizeController.java` | `GET /oauth2/authorize` — validates request, redirects with code or error |
| `oauth-oidc/src/main/java/.../service/AuthorizeService.java` | Request validation + auth code issuance |
| `oauth-oidc/src/main/java/.../dto/AuthorizationRequest.java` | Record: `clientId`, `redirectUri`, `responseType`, `scope`, `state`, `codeChallenge`, `codeChallengeMethod`, `isPublic` |
| `oauth-oidc/src/main/java/.../dto/OAuthErrorResponse.java` | RFC 6749 §4.1.2.1 error record: `error`, `errorDescription`, `errorUri`, `state` |
| `oauth-oidc/src/main/java/.../util/PkceUtils.java` | RFC 7636: `generateCodeVerifier()`, `deriveCodeChallenge()`, `verifyCodeChallenge()`, `isValidVerifier()`, `isValidChallenge()` |
| `oauth-oidc/src/test/java/.../service/AuthorizeServiceTest.java` | 10 tests: redirect_uri mismatch, PKCE requirements, scope validation, code issuance |

**`AuthorizeService.validateRequest()` logic:**
1. `client_id` required and must exist
2. `redirect_uri` exact-match against registered URIs
3. `response_type` must be `"code"`
4. Public clients require `code_challenge` + `code_challenge_method=S256`
5. Scope validated against client's allowed scopes

**`AuthorizeService.issueAuthCode()` logic:**
- Generates 32-byte random code (Base64URL, no padding)
- Stores `AuthCode` in PostgreSQL with 5-minute TTL
- Redirects to `redirect_uri?code=<CODE>&state=<STATE>`

**Key decision:** PKCE is enforced for all clients where `is_public=true` in the DB, not just by convention. This matches RFC 7636 requirement for public clients.

---

### Task 7 — `/token` Endpoint — Auth Code Exchange (`0a645ed`)

RFC 6749 §3.2 + RFC 7636 §4.6 code exchange.

**Files created:**

| File | Description |
|------|-------------|
| `oauth-oidc/src/main/java/.../controller/TokenController.java` | `POST /oauth2/token` — dispatches by grant type |
| `oauth-oidc/src/main/java/.../service/TokenService.java` | Handles all grant types: auth_code, client_credentials, refresh_token |
| `oauth-oidc/src/main/java/.../dto/TokenRequest.java` | Record: `grantType`, `code`, `redirectUri`, `codeVerifier`, `clientId`, `clientSecret`, `refreshToken`, `scope` |
| `oauth-oidc/src/main/java/.../dto/TokenResponse.java` | Record: `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, `idToken`, `scope`, `error`, `errorDescription` |
| `oauth-oidc/src/test/java/.../service/TokenServiceTest.java` | 13 tests: all grant types, error cases, PKCE verification |

**`handleAuthorizationCodeGrant()` logic:**
1. Code must exist in DB and not be consumed
2. `client_id` must match the code's client
3. PKCE verified via `PkceUtils.verifyCodeChallenge(codeVerifier, storedChallenge, method)`
4. Code marked consumed atomically (`consumedAt = NOW`)
5. Issues opaque `access_token` (32-byte random, Base64URL) stored in DB
6. Issues `refresh_token` (32-byte random, Base64URL) stored in DB with 7-day TTL

**`TokenController` — dual client auth:**
- `client_id` from body (form param)
- `client_id` + `client_secret` via HTTP Basic auth header (RFC 6749 §2.3.1)
- Both checked; whichever is present is used

---

### Task 8 — `/token` — Client Credentials (`94caa94`)

RFC 6749 §4.2 machine-to-machine grant.

**Added to `TokenService`:**

| Change | Description |
|--------|-------------|
| `handleClientCredentialsGrant()` | Validates secret hash, issues access token scoped to client's allowed scopes |
| No refresh token issued | Client credentials represent a client, not a user — no subject for refresh |

**Client secret hashing:**
- SHA-256 of `client_secret` → stored as `client_secret_hash` in DB
- `client_secret` never stored in plaintext

---

### Task 9 — Demo Resource Protected API (`d9f055a`)

**Files created:**

| File | Description |
|------|-------------|
| `demo-resource/src/main/java/.../controller/ResourceController.java` | `GET /api/resource` (protected), `GET /api/health` (public) |
| `demo-resource/src/main/java/.../security/TokenValidationService.java` | DB lookup: `findByJtiAndRevokedFalse(jti)` → check expiry → check type=access_token |
| `demo-resource/src/main/java/.../security/BearerTokenAuthenticationFilter.java` | Extracts Bearer token, calls `TokenValidationService`, sets `SecurityContext` |
| `demo-resource/src/main/java/.../security/ResourceSecurityConfig.java` | Spring Security config: `/api/health` permitAll, all others require authentication |
| `demo-resource/src/test/java/.../ResourceControllerTest.java` | 5 tests: 401 without token, 200 with valid token, 401 with revoked token |
| `demo-resource/pom.xml` | `spring-boot-starter-security`, `spring-security-test`, depends on `auth-core` |

**`TokenValidationService.validate()` logic:**
1. Lookup token by `jti` where `revoked=false`
2. Check `expiresAt > NOW`
3. Check `type == access_token`
4. Returns `ValidationResult(valid, subject, scope)`

**Phase 2 vs Phase 3 note:** In Phase 2, access tokens are opaque random strings validated against the DB. In Phase 3 (future), `TokenValidationService` will be replaced with RS256 JWT validation using the JWKS endpoint.

---

### Phase 2 Refactors + Fixes

**Lombok refactor (`04fa492`):** Replaced explicit getters/setters with `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` on all entities and DTOs.

**AuthCode entity changes (`94caa94`):** Added `nonce` field to `AuthCode.java` — reserved for Phase 3 OIDC ID token binding.

**Demo resource 401 fix (`b1dcf97`):** `BearerTokenAuthenticationFilter` now correctly leaves the `SecurityContext` empty on invalid token rather than setting an anonymous authentication, ensuring Spring Security returns 401 instead of allowing anonymous access.

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| PKCE enforced for all `is_public=true` clients | RFC 7636 requires S256 for public clients; missing verifier = `invalid_grant` |
| Opaque access tokens in Phase 2 | Simple DB lookup; Phase 3 upgrades to RS256 JWT validated via JWKS |
| SHA-256 for client secret hashing | One-way hash; secrets never stored in plaintext |

---

## Key Validation Rules Summary

| Endpoint | Error | Condition |
|----------|-------|-----------|
| `GET /oauth2/authorize` | `invalid_request` | `redirect_uri` doesn't exactly match registered URI |
| `GET /oauth2/authorize` | `invalid_request` | Public client missing `code_challenge` or non-S256 method |
| `GET /oauth2/authorize` | `invalid_scope` | Requested scope not in client's allowed scopes |
| `POST /oauth2/token` | `invalid_grant` | Auth code expired (5min TTL) or already consumed |
| `POST /oauth2/token` | `invalid_grant` | `code_verifier` doesn't match stored `code_challenge` |
| `POST /oauth2/token` | `invalid_client` | Wrong `client_secret` for confidential client |
| `POST /oauth2/token` | `invalid_grant` | Refresh token revoked or reused (rotation enforced) |

---

## What's Next (Phase 3 — OIDC Layer)

- **Task 10:** OIDC Discovery (`/.well-known/openid-configuration`) + JWKS (`/.well-known/jwks.json`)
- **Task 11:** ID Token issuance (RS256 JWT) in auth code exchange
- **Task 12:** `/userinfo` endpoint returning OIDC claims
