# Testing Guide

**Collection:** `postman/iam-protocol-engine.json`

This guide covers all implemented endpoints across Phase 2 (OAuth 2.0 Core), Phase 3 (OIDC Layer), and Phase 4 (Token Lifecycle). For unit test commands, see each module's `src/test` directory.

---

## Setup

```bash
# 1. Start infrastructure
docker compose -f infra/docker-compose.yml up -d

# 2. Run Flyway migrations (V1 = Phase 1 base schema)
./mvnw flyway:migrate -pl backend/auth-core

# 3. If testing Phase 4 features on a pre-existing DB, apply V2 migration manually:
docker exec iam-postgres psql -U iam_user -d iam_engine -c \
  "ALTER TABLE token ADD COLUMN family_id VARCHAR(128); \
   CREATE INDEX idx_token_family_id ON token(family_id) WHERE family_id IS NOT NULL;"

# 4. Seed a test client (see Client Registration below)

# 5. Start the app
./mvnw spring-boot:run -pl backend/api-gateway
```

Verify the app is running:

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Client Registration

Run this SQL before any testing:

```sql
-- Public client (PKCE required, no client_secret)
INSERT INTO oauth_client (client_id, client_secret_hash, redirect_uris, allowed_scopes, grant_types, is_public)
VALUES (
  'test-client',
  '',
  'https://app.example.com/callback',
  'openid profile email',
  'authorization_code',
  true
);
```

For confidential client (Basic auth at `/token`):

```sql
-- SHA-256 of 'secret1' = jGZIHLlSkhZ7NKwTgR5hSVRcABiqXBDTLbEXZWHzNFI=
INSERT INTO oauth_client (client_id, client_secret_hash, redirect_uris, allowed_scopes, grant_types, is_public)
VALUES (
  'confidential-client',
  'jGZIHLlSkhZ7NKwTgR5hSVRcABiqXBDTLbEXZWHzNFI=',
  'https://app.example.com/callback',
  'openid profile email',
  'authorization_code',
  false
);
```

### Postman Variables

| Variable | Value |
|----------|-------|
| `baseUrl` | `http://localhost:8080` |
| `client_id` | `test-client` |
| `redirect_uri` | `https://app.example.com/callback` |
| `code_verifier` | `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` |
| `client_secret` | `test-secret` |
| `client_secret_basic` | `base64(test-client:test-secret)` |

---

## Phase 2 — OAuth 2.0 Core

### Authorize (Auth Code + PKCE)

**`GET /oauth2/authorize`**

```
{{baseUrl}}/oauth2/authorize?
  client_id={{client_id}}&
  redirect_uri={{redirect_uri}}&
  response_type=code&
  scope=openid%20profile%20email&
  state=xyz&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256&
  subject=user1
```

- `subject=user1` is the pre-authenticated user ID (Phase 5 replaces this with a login page)
- **Expected:** `302` redirect to `{{redirect_uri}}?code=<AUTH_CODE>&state=xyz`
- Extract `code` from the redirect URL and set it as `AUTH_CODE` in Postman

**Validation checks:**
- Missing or mismatched `redirect_uri` → `error=invalid_request`
- `response_type` != `code` → `error=unsupported_response_type`
- Public client without `code_challenge` → `error=invalid_request`
- Unknown `client_id` → `error=invalid_client`

---

### Token (Auth Code Exchange)

**`POST /oauth2/token`**

```
{{baseUrl}}/oauth2/token

Body (urlencoded):
  grant_type    = authorization_code
  code          = {{AUTH_CODE}}
  code_verifier = {{code_verifier}}
  redirect_uri  = {{redirect_uri}}
  client_id     = {{client_id}}
```

**Response:**
```json
{
  "access_token": "<opaque token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "<refresh token>",
  "id_token": "<Phase 3>",
  "scope": "openid profile email"
}
```

**Validation checks:**
- Wrong `code_verifier` → `invalid_grant`
- Expired or already-consumed code → `invalid_grant`
- `redirect_uri` mismatch → `invalid_grant`
- Missing `code` → `invalid_request`

---

### Token (Client Credentials)

**`POST /oauth2/token`** (confidential client with Basic auth)

```
{{baseUrl}}/oauth2/token

Header: Authorization: Basic {{client_secret_basic}}
Body (urlencoded):
  grant_type = client_credentials
  scope      = openid profile email
```

**Response:**
```json
{
  "access_token": "<opaque token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email"
}
```

- No `refresh_token` for client_credentials (no user subject)
- No `id_token` for client_credentials (ID tokens require a user)

**Validation checks:**
- Wrong secret → `invalid_client`
- Scope exceeds client's allowed scopes → scope is narrowed to allowed set

---

### Token (Refresh Rotation)

**`POST /oauth2/token`**

```
{{baseUrl}}/oauth2/token

Body (urlencoded):
  grant_type    = refresh_token
  refresh_token = {{REFRESH_TOKEN}}
  client_id     = {{client_id}}
```

**Response:**
```json
{
  "access_token": "<new access token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "<new refresh token>",
  "scope": "openid profile email"
}
```

- Old refresh token is **atomically revoked** on use (Phase 4: family binding)
- Reusing the old refresh token → `invalid_grant` + **all tokens in that family are revoked** (including the access token issued alongside the original refresh token)

---

### Demo Resource (Protected API)

**`GET /api/resource` — no token → 401**

```
{{baseUrl}}/api/resource
```

**`GET /api/resource` — valid token → 200**

```
{{baseUrl}}/api/resource
Header: Authorization: Bearer {{access_token}}
```

Response:
```json
{
  "message": "You accessed a protected resource",
  "subject": "user1"
}
```

---

## Phase 3 — OIDC Layer

### OIDC Discovery

**`GET /.well-known/openid-configuration`**

```
{{baseUrl}}/.well-known/openid-configuration
```

**Checks:**
- `issuer` = `http://localhost:8080`
- `authorization_endpoint` = `http://localhost:8080/oauth2/authorize`
- `token_endpoint` = `http://localhost:8080/oauth2/token`
- `jwks_uri` = `http://localhost:8080/.well-known/jwks.json`
- `response_types_supported` = `["code"]`
- `id_token_signing_alg_values_supported` = `["RS256"]`
- `code_challenge_methods_supported` = `["S256"]`
- `scopes_supported` includes `"openid"`, `"profile"`, `"email"`

---

### JWKS

**`GET /.well-known/jwks.json`**

```
{{baseUrl}}/.well-known/jwks.json
```

**Checks:**
- Top-level key is `"keys"` (array)
- Each key object has: `kty=RSA`, `use=sig`, `alg=RS256`, `kid`, `n`, `e`

---

### Key Rotation

**`POST /.well-known/jwks.json`**

```
{{baseUrl}}/.well-known/jwks.json
```

Response: `{"status":"rotated","new_kid":"...","message":"..."}`

After rotating, `GET /.well-known/jwks.json` should contain **2 keys**. Old keys remain valid for verifying existing tokens.

---

### ID Token Issuance (Full PKCE Flow)

ID tokens are returned from the **Token (Auth Code Exchange)** request when `openid` scope is requested.

**Step 1 — Authorize**

```
{{baseUrl}}/oauth2/authorize?
  client_id={{client_id}}&
  redirect_uri={{redirect_uri}}&
  response_type=code&
  scope=openid%20profile%20email&
  state=xyz&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256&
  subject=user1
```

→ `302` with `code=<AUTH_CODE>`

**Step 2 — Token Exchange**

```
{{baseUrl}}/oauth2/token

Body (urlencoded):
  grant_type    = authorization_code
  code          = {{AUTH_CODE}}
  code_verifier = {{code_verifier}}
  redirect_uri  = {{redirect_uri}}
  client_id     = {{client_id}}
```

Response includes **`id_token`** (Phase 3 deliverable).

**Step 3 — Decode the ID Token**

Split `id_token` on `.` → `HEADER.PAYLOAD.SIGNATURE`

Base64URL-decode the **PAYLOAD** segment:

```bash
# Example using Python
python3 -c "import base64, json; print(json.dumps(json.loads(base64.urlsafe_b64decode('PAYLOAD_HERE + '===')), indent=2))"
```

**Required claims in decoded payload:**
- `iss` = `http://localhost:8080`
- `sub` = `user1` (matches the `subject` passed to `/authorize`)
- `aud` = `test-client`
- `iat` ≈ current Unix timestamp
- `exp` = `iat + 3600`
- `nonce` — present if nonce was included in `/authorize`

**Step 4 — Verify Signature**

Use the RSA public key from `GET /.well-known/jwks.json`. Verify the signature over `HEADER.BASE64URL(PAYLOAD)` using RS256 (RSA + SHA-256). jwt.io can be used for manual verification by pasting the full `id_token` and the public key in JWK format.

---

### /userinfo

**No Authorization header → 401**

```
GET {{baseUrl}}/userinfo
```

**Malformed Authorization header → 401**

```
GET {{baseUrl}}/userinfo
Authorization: Basic invalid
```

**Valid Bearer token → 200**

```
GET {{baseUrl}}/userinfo
Authorization: Bearer {{access_token}}
```

**Response checks:**
- `sub` = matches the subject from the auth code exchange (e.g., `user1`)
- `scope` = matches the scopes granted (e.g., `openid profile email`)
- If `profile` scope was granted → response may include `name` (placeholder, SCIM lookup is a future phase)
- If `email` scope was granted → response may include `email` (placeholder, SCIM lookup is a future phase)

---

## Phase 4 — Token Lifecycle

### Token Introspection (RFC 7662)

**`POST /oauth2/introspect`**

```
{{baseUrl}}/oauth2/introspect

Body (urlencoded):
  token             = {{ACCESS_TOKEN}}
  token_type_hint   = access_token   (optional)
```

**Active token response:**
```json
{
  "active": true,
  "sub": "user1",
  "client_id": "test-client",
  "scope": "openid profile email",
  "token_type": "access_token",
  "exp": 1735731354,
  "iat": 1735727754
}
```

**Inactive/unknown token response:**
```json
{ "active": false }
```

**Validation checks:**
- Token revoked → `active: false`
- Token expired → `active: false`
- Unknown token → `active: false`
- `token_type_hint` is advisory only — the server determines the actual type

**Works for both access tokens and refresh tokens.** Pass `token_type_hint=refresh_token` to optimize the lookup when the token type is known ahead of time.

---

### Token Revocation (RFC 7009)

**`POST /oauth2/revoke`**

```
{{baseUrl}}/oauth2/revoke

Body (urlencoded):
  token             = {{ACCESS_TOKEN}}
  token_type_hint   = access_token   (optional)
```

**Response:** `200 OK` (always — per RFC 7009 §2.1, the server returns 200 even if the token was invalid or already revoked, to prevent token enumeration attacks)

**Effect on revocation:**
- If revoked token is an **access token** → only that token is revoked
- If revoked token is a **refresh token** → the refresh token AND the paired access token (same token family) are both revoked immediately

**Verification steps:**

```
1. GET /api/resource with {{ACCESS_TOKEN}}        → 200 OK
2. POST /oauth2/revoke with {{ACCESS_TOKEN}}       → 200 OK
3. POST /oauth2/introspect with {{ACCESS_TOKEN}}  → active: false
4. GET /api/resource with {{ACCESS_TOKEN}}        → 401 Unauthorized
```

**Refresh token revocation (family sweep):**

```
1. POST /oauth2/token (refresh)                    → new access_token + new refresh_token
2. POST /oauth2/revoke with {{REFRESH_TOKEN}}     → 200 OK
3. POST /oauth2/introspect with {{REFRESH_TOKEN}} → active: false
4. POST /oauth2/introspect with {{ACCESS_TOKEN}}  → active: false  (same family, swept)
```

---

## End-to-End Flows

### Phase 2 — Auth Code + PKCE

```
1. Authorize       → GET  /oauth2/authorize           (302 with code)
2. Token Exchange  → POST /oauth2/token               (access_token + refresh_token)
3. Protected API   → GET  /api/resource (Bearer)      (200 + resource data)
```

### Phase 2 — Client Credentials

```
1. Token          → POST /oauth2/token (Basic auth)  (access_token only)
2. Protected API  → GET  /api/resource (Bearer)       (200, subject=null)
```

### Phase 2 — Refresh Rotation

```
1. Token (refresh) → POST /oauth2/token              (new access + new refresh)
2. Token (reuse old refresh) → POST /oauth2/token   (invalid_grant)
```

### Phase 3 — OIDC

```
1. OIDC Discovery  → GET  /.well-known/openid-configuration
2. JWKS           → GET  /.well-known/jwks.json
3. Authorize      → GET  /oauth2/authorize
4. Token          → POST /oauth2/token                (access_token + id_token + refresh_token)
5. Decode ID token → split + Base64URL decode payload
6. Verify signature → against JWKS
7. UserInfo       → GET  /userinfo (Bearer)          (sub, scope)
```

### Phase 4 — Token Lifecycle

```
1. Token (refresh)      → POST /oauth2/token              (new access + new refresh)
2. Introspect new       → POST /oauth2/introspect        (active: true, check metadata)
3. Revoke refresh       → POST /oauth2/revoke             (200 OK, family swept)
4. Introspect after     → POST /oauth2/introspect        (active: false)
5. Access token revoked → GET  /api/resource             (401 Unauthorized)
```

---

### Full Phase 2 + 3 + 4 Flow

```
1. OIDC Discovery → GET  /.well-known/openid-configuration
2. JWKS          → GET  /.well-known/jwks.json
3. Authorize     → GET  /oauth2/authorize
4. Token         → POST /oauth2/token               (access_token + id_token + refresh_token)
5. Decode ID     → split + Base64URL decode payload + verify signature against JWKS
6. UserInfo      → GET  /userinfo (Bearer)           (sub, scope)
7. Refresh       → POST /oauth2/token               (new access + new refresh)
8. Introspect    → POST /oauth2/introspect           (active: true)
9. Revoke        → POST /oauth2/revoke               (200 OK)
10. Verify gone  → POST /oauth2/introspect           (active: false)
```

---

## Phase 6 — SCIM 2.0

### Create User (Joiner)

**`POST /scim/v2/Users`**

```bash
curl -X POST http://localhost:8080/scim/v2/Users \
  -H "Content-Type: application/json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "john.doe",
    "displayName": "John Doe",
    "emails": [{ "value": "john.doe@example.com", "primary": true }],
    "active": true
  }'
```

→ `201 Created` with `Location: http://localhost:8080/scim/v2/Users/{id}`

**JML Joiner hook:** `ScimUserService.createUser()` logs `JML [joiner] user created: userName=...`

---

### List Users

**`GET /scim/v2/Users?startIndex=1&count=10`**

```bash
curl http://localhost:8080/scim/v2/Users?startIndex=1\&count=10 \
  -H "Authorization: Bearer {{access_token}}"
```

Filter: `?filter=userName eq "john.doe"`

---

### Get User

**`GET /scim/v2/Users/{id}`**

```bash
curl http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer {{access_token}}"
```

→ `200 OK` with SCIM User schema (RFC 7643)

---

### Replace User (Mover)

**`PUT /scim/v2/Users/{id}`**

```bash
curl -X PUT http://localhost:8080/scim/v2/Users/{id} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {{access_token}}" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "john.doe.updated",
    "displayName": "John D. Updated",
    "emails": [{ "value": "john.updated@example.com", "primary": true }],
    "active": false
  }'
```

→ `200 OK` with updated user

---

### Delete User (Leaver + Token Revocation)

**`DELETE /scim/v2/Users/{id}`**

```bash
curl -X DELETE http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer {{access_token}}"
```

→ `204 No Content`

**JML Leaver hook:** Before deletion, `TokenService.revokeAllTokensForUser(userName)` is called, revoking all active tokens for that subject. Check server logs for `JML [leaver] user deleted: userName=X, tokens_revoked=N`.

**Verification:**
```bash
# Tokens for deleted user should be revoked
curl -X POST http://localhost:8080/oauth2/introspect \
  -d "token={{access_token_for_deleted_user}}"
# → { "active": false }
```

---

### Create Group

**`POST /scim/v2/Groups`**

```bash
curl -X POST http://localhost:8080/scim/v2/Groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {{access_token}}" \
  -d '{"schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"], "displayName": "Engineering"}'
```

→ `201 Created`

---

### Add Member to Group (PATCH)

**`PATCH /scim/v2/Groups/{id}`**

```bash
curl -X PATCH http://localhost:8080/scim/v2/Groups/{id} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {{access_token}}" \
  -d '[
    { "op": "add", "members": [{ "value": "{{user_id}}" }] }
  ]'
```

→ `200 OK`

---

## Phase 7 — SAML 2.0 SP

### SP Metadata

**`GET /saml/metadata`**

```bash
curl http://localhost:8080/saml/metadata
```

→ `200 OK` with XML. Metadata is signed (RSA-SHA256). Contains:
- `<EntityDescriptor>` with `entityID`
- `<AssertionConsumerService>` binding `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST`
- `<KeyDescriptor>` with SP signing key

**Register this metadata with your IdP.**

---

### Initiate SSO (SP-initiated)

**`GET /saml/initiate?client_id=CLIENT&redirect_uri=URI`**

```bash
curl -v "http://localhost:8080/saml/initiate?client_id=test-client&redirect_uri=https://app.example.com/callback"
```

→ `302` redirect to IdP SSO URL with URL-encoded `SAMLRequest` query param (signed AuthnRequest)

**Validation checks:**
- Unknown `client_id` → `400 Bad Request`
- Unregistered `redirect_uri` → `400 Bad Request`

---

### ACS (Assertion Consumer Service)

**`POST /saml/acs`** (IdP posts this after authentication)

```bash
curl -X POST http://localhost:8080/saml/acs \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "SAMLResponse=BASE64_ENCODED_RESPONSE&RelayState=..."
```

**What happens on success:**
1. Validates XML signature (RSA-SHA256) with IdP certificate
2. Rejects if `InResponseTo` doesn't match stored request ID (replay protection)
3. Rejects if `Destination` doesn't match ACS URL
4. Rejects if `NotBefore` / `NotOnOrAfter` conditions fail
5. Rejects if `AudienceRestriction` doesn't match SP entity ID
6. Extracts `NameID` from assertion
7. Issues access_token + refresh_token + id_token (same as OAuth flow)
8. `302` redirects to `redirect_uri?access_token=...&id_token=...&state=...`

**SAML → OIDC mapping:**
- SAML `NameID` → OAuth `sub` claim
- SAML attributes → mapped via configurable claim mapping (future)

---

## Common Errors

| Error | Cause |
|-------|-------|
| `invalid_grant` on token exchange | Auth code expired (5min TTL) or already consumed |
| `invalid_grant` — PKCE failure | `code_verifier` doesn't match `code_challenge` |
| `invalid_request` on `/authorize` | `redirect_uri` doesn't exactly match registered URI |
| `unsupported_response_type` | `response_type` is not `code` |
| `invalid_client` | Wrong client secret, or client_id not found |
| `id_token` missing | `openid` scope was not requested in `/authorize` |
| `401` on `/userinfo` | Access token expired, revoked, or jti not found |
| `401` on `/api/resource` | Missing or invalid Bearer token |
| `active: false` on introspect | Token expired, revoked, or not found |
| Revoke returns 500 | Check token is not null; RevocationController requires `@Transactional` |
