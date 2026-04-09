# Phase 3 Code Change Summary

**Branch:** `phase-3` (HEAD)
**Commits:** 6 (from `b03bb63` to `24837bb`)
**Lines changed:** ~+1,272 across 15 files

---

## Phase 3 — OIDC Layer

### Task 10 — OIDC Discovery + JWKS (`b03bb63`)

RFC 8414 (OIDC Discovery) + RFC 7517 §5 (JWKS) + RFC 7518 §6.3 (RSA JWK).

**Files created:**

| File | Description |
|------|-------------|
| `oauth-oidc/src/main/java/.../controller/DiscoveryController.java` | `GET /.well-known/openid-configuration` |
| `oauth-oidc/src/main/java/.../controller/JwksController.java` | `GET /.well-known/jwks.json` + `POST /.well-known/jwks.json` (key rotation) |
| `oauth-oidc/src/main/java/.../security/JwksService.java` | Key store management, JWKS response building, key rotation |
| `oauth-oidc/src/main/java/.../security/RsaKeyPairGenerator.java` | RSA key generation, PKCS12 keystore persistence, kid derivation from cert thumbprint |

**`DiscoveryController` — OIDC metadata fields:**
```java
issuer, authorization_endpoint, token_endpoint, jwks_uri,
response_types_supported=["code"], subject_types_supported=["public"],
id_token_signing_alg_values_supported=["RS256"],
scopes_supported=["openid","profile","email"],
token_endpoint_auth_methods_supported=["client_secret_basic","client_secret_post","none"],
claims_supported=["iss","sub","aud","exp","iat","nonce","name","email","scope"],
grant_types_supported=[...], code_challenge_methods_supported=["S256"]
```

**`JwksService` — key design:**

- **`kid` derivation:** SHA-256 thumbprint of the X.509 certificate (RFC 7517 §4.5), Base64URL-encoded without padding. Stable across restarts for the same key.
- **Keystore:** PKCS12 file at configurable path (`iam.keystore.path`, default `/tmp/iam-engine/keystore.p12`). Each key stored with alias = `kid`.
- **`getCurrentPrivateKey()`:** Returns the most recent key for signing new tokens.
- **`getPublicKey(kid)`:** Returns a key for validating tokens signed with that `kid`.
- **`getAllJwks()`:** Returns all keys (oldest-first) so any valid token can be verified.
- **`rotateKey()`:** Generates new RSA key pair + self-signed cert, adds to keystore, reloads. Returns new `kid`.

**`RsaKeyPairGenerator` — key facts:**
- Uses `KeyPairGenerator` with BouncyCastle provider (2048-bit RSA by default)
- Self-signed X.509 certificate generated with BouncyCastle `JcaX509v3CertificateBuilder`
- 1-year certificate validity, `digitalSignature + keyEncipherment` key usage
- Keystore persists to filesystem — survives app restarts

**`oauth-oidc/pom.xml` updated:** Added `bouncycastle-bcprov-jdk18on`, `bouncycastle-bcpkix-jdk18on` dependencies.

---

### Task 11 — ID Token Issuance (`bea44ec`)

OIDC Core 1.0 §3.1.3.3 ID Token in auth code exchange.

**Files created:**

| File | Description |
|------|-------------|
| `oauth-oidc/src/main/java/.../security/IdTokenGenerator.java` | RS256-signed JWT ID token generation, built manually (no library) |

**`IdTokenGenerator.generateIdToken(subject, clientId, nonce)` — claims:**
`iss` (from config), `sub` (user subject), `aud` (client_id), `iat` (now), `exp` (now+3600), `nonce` (if present).

**JWS Compact Serialization (built by hand):**
```
BASE64URL( UTF-8( {"alg":"RS256","typ":"JWT"} ) ) || '.' ||
BASE64URL( UTF-8( payload_json ) ) || '.' ||
BASE64URL( RSASSA-PKCS1-v1_5-SHA256( signing_input ) )
```

**Signing:** Uses the current RSA private key from `JwksService.getCurrentPrivateKey()`. Signature via `Signature.getInstance("SHA256withRSA")`.

**Minimal JSON serializer:** Custom `toJson(Map)` method handles String, Number, Boolean, List, Map — no external library dependency.

**Integration in `TokenService.handleAuthorizationCodeGrant()`:**
```java
String idToken = idTokenGenerator.generateIdToken(
    subject, authCode.getClientId(), authCode.getNonce());
// returned in TokenResponse as idToken field
```

**`TokenService.handleAuthorizationCodeGrant()` updated:** After issuing access + refresh token, calls `idTokenGenerator.generateIdToken()` and includes `idToken` in response.

---

### Task 12 — `/userinfo` Endpoint (`0f484c3`)

OIDC Core 1.0 §5.3.

**Files created:**

| File | Description |
|------|-------------|
| `oauth-oidc/src/main/java/.../controller/UserInfoController.java` | `GET /userinfo` — Bearer token required |

**`UserInfoController.userInfo()` logic:**
1. Extract Bearer token from `Authorization` header
2. Lookup by `jti` where `revoked=false`
3. Check expiry and token type == `access_token`
4. Return claims: `sub` (always), `scope` (always), `name` (if `profile` scope), `email` (if `email` scope)

**Scope-driven claims:** Only `sub` and `scope` are currently populated. `name`/`email` return `sub` as placeholder. Full population requires SCIM user lookup (Phase 6).

---

### Supporting Changes

**`application.properties` replaces `application.yml` (`56c07a8`):**
- `api-gateway/src/main/resources/application.yml` deleted
- `api-gateway/src/main/resources/application.properties` created with `iam.issuer=http://localhost:8080`, `iam.keystore.path`, `iam.keystore.password`

**`auth-core` entities updated (`94caa94`):** `AuthCode` gained `nonce` field in Phase 2, used here for ID token binding.

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| `kid` derived from cert SHA-256 thumbprint | Stable identity across restarts; matches RFC 7517 §4.5 |
| PKCS12 keystore for key persistence | Standard format, supports multiple key aliases (rotation), password-protected |
| ID token built without a JWT library | Demonstrates RFC-level understanding of JWS compact serialization |
| BouncyCastle for self-signed cert generation | Full control over certificate fields; Java's `keytool` is cumbersome to load programmatically |
| Self-signed X.509 certificate wrapping RSA key | Provides full key material for JWKS; thumbprint used as stable `kid` |

---

## Key Validation Rules Summary

| Endpoint | Error | Condition |
|----------|-------|-----------|
| `GET /userinfo` | `401` | Missing, expired, or revoked Bearer token |

---

## What's Next (Phase 4 — Token Lifecycle)

- **Task 13:** `POST /token` with `grant_type=refresh_token` — refresh token rotation (already partially implemented in Phase 2 `TokenService.handleRefreshTokenGrant()`)
- **Task 14:** `POST /introspect` (RFC 7662) + `POST /revoke` (RFC 7009)
- demo-resource will upgrade from DB-lookup tokens to RS256 JWT validation using JWKS
