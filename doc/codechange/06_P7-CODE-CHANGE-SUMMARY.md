# Phase 7 Code Change Summary

**Phase:** SAML 2.0 SP + SCIM JML Lifecycle
**Branch:** `phase-7` (merged to `phase-6`)
**RFCs:** SAML 2.0 (OASIS), RFC 7523 (SAML Profile for OAuth), RFC 7644 (SCIM Protocol)

---

## What Was Built

Phase 7 implements two distinct deliverables:

1. **SAML 2.0 Service Provider** — SP-initiated SSO with signed AuthnRequests, HTTP-POST ACS, 7-step assertion validation, and SAML → OIDC token bridge
2. **SCIM JML Lifecycle Hooks** — Leaver (delete user) triggers immediate token revocation via `TokenService.revokeAllTokensForUser()`

## Modules Changed

| Module | Change |
|--------|--------|
| `backend/saml-federation/` | **New module** — SAML SP: metadata, AuthnRequest, ACS, assertion validation, SAML→OIDC bridge |
| `backend/oauth-oidc/` | Added `TokenService.issueTokensForSamlUser()` and `TokenService.revokeAllTokensForUser()` |
| `backend/scim/` | Injected `TokenService`; `deleteUser()` → leaver token revocation; `createUser()` → joiner log event |
| `backend/api-gateway/` | Added SAML configuration properties (`saml.sp.*`, `saml.idp.*`); added `saml-federation` module dependency |

---

## Task 20: SAML SP Metadata + AuthnRequest

### Files Added

#### `backend/saml-federation/pom.xml` (new)
New Maven module. Depends on `auth-core`, `oauth-oidc`. Key dependencies:
- `opensaml-core`, `opensaml-saml-api`, `opensaml-saml-impl` (4.0.0) — XML object model and unmarshalling
- `org.apache.santuario:xmlsec` (4.0.4) — XML signing support

#### `backend/saml-federation/src/main/java/com/iam/saml/config/OpenSamlConfig.java` (new)
Bootstraps OpenSAML using `InitializationService.initialize()`. Provides configured `DocumentBuilderFactory` with security hardening (DTD disabled, XXE disabled).

#### `backend/saml-federation/src/main/java/com/iam/saml/service/SamlMetadataService.java` (new)
Generates signed SP metadata XML:
- Builds `EntityDescriptor` with `SPSSODescriptor`
- Embeds SP signing public key from `JwksService` keystore
- Adds `AssertionConsumerService` with HTTP-POST binding
- Signs metadata using JSR 105 RSA-SHA256 with inclusive canonical XML

#### `backend/saml-federation/src/main/java/com/iam/saml/service/SamlAuthnRequestService.java` (new)
Builds and signs `AuthnRequest` XML:
- Generates unique request ID (`_` + 43-char random Base64URL string)
- Stores request ID in `ConcurrentHashMap<String, Instant>` with 5-min TTL for replay protection
- Builds `AuthnRequest` with Issuer, NameIDPolicy, AssertionConsumerServiceURL
- Signs using JSR 105 RSA-SHA256
- Returns SAMLRequest (Base64URL-encoded) + RelayState

#### `backend/saml-federation/src/main/java/com/iam/saml/controller/SamlController.java` (new)
Exposes:
- `GET /saml/metadata` — returns signed SP metadata XML
- `GET /saml/initiate?client_id=&redirect_uri=` — builds AuthnRequest, redirects to IdP

### Files Modified

#### `backend/oauth-oidc/src/main/java/com/iam/oauth/security/JwksService.java`
Added `public KeyStore getKeystore()` and `public char[] getKeystorePassword()` to expose the keystore to `SamlMetadataService` for signing operations.

---

## Task 21: ACS Endpoint + Assertion Validation

### Files Added

#### `backend/saml-federation/src/main/java/com/iam/saml/service/SamlAssertionValidator.java` (new)
7-step SAML assertion validation:

1. **XML Signature** — JSR 105 DOMValidateContext with IdP public key; rejects if signature invalid
2. **InResponseTo Replay Protection** — checks request ID exists in pending map and not expired; removes after check
3. **Destination** — matches AssertionConsumerServiceURL
4. **NotBefore / NotOnOrAfter** — system clock comparison
5. **AudienceRestriction** — SP entityID must be in audience list
6. **SubjectConfirmation** — separate NotOnOrAfter timing check on confirmation data
7. **Consume Request ID** — removes from pending map to prevent replay

Key design: Uses OpenSAML `XMLObjectProviderRegistrySupport.getUnmarshallerFactory()` for unmarshalling; JSR 105 `DOMValidateContext` for signature validation. Does NOT use OpenSAML's signing APIs (they are poorly structured in 4.0.0).

#### `backend/saml-federation/src/main/java/com/iam/saml/controller/SamlAcsController.java` (new)
`POST /saml/acs` (HTTP-POST binding):
- Decodes Base64 SAMLResponse XML
- Delegates to `SamlAssertionValidator`
- On success: calls `SamlToOidcTokenService.bridge()` then redirects with tokens

### ValidationResult Record

```java
public record ValidationResult(
    boolean success,
    String nameId,
    String sessionIndex,
    String relayState,
    String error
) {}
```

---

## Task 22: SAML → OIDC Claim Bridge

### Files Added/Modified

#### `backend/saml-federation/src/main/java/com/iam/saml/service/SamlToOidcTokenService.java` (new)
Maps SAML NameID to OIDC tokens:
- `bridge(nameId, sessionIndex, relayState, nonce)` — decodes RelayState JSON, calls `TokenService.issueTokensForSamlUser()`, builds redirect URL with tokens as query params
- `buildRedirectUrl()` — formats tokens as `access_token=...&token_type=Bearer&expires_in=...&id_token=...&refresh_token=...&state=...`

#### `backend/oauth-oidc/src/main/java/com/iam/oauth/service/TokenService.java`
Added `issueTokensForSamlUser(subject, clientId, nonce, scope)`:
- Validates client exists
- Resolves scope against client's allowed scopes
- Issues access_token + refresh_token (with family_id) + id_token (RS256 JWT with sub = SAML NameID)
- Redirects tokens as query params (SAML convention, not OIDC fragment)

---

## Task 23: SCIM JML Lifecycle → Token Revocation

### Files Modified

#### `backend/scim/pom.xml`
Added `oauth-oidc` module dependency (for `TokenService`).

#### `backend/scim/src/main/java/com/iam/scim/service/ScimUserService.java`
- Injects `TokenService` via constructor
- **`createUser()` (joiner):** Logs `JML [joiner] user created: userName=...`
- **`deleteUser()` (leaver):** Fetches user by ID, calls `tokenService.revokeAllTokensForUser(userName)`, then hard-deletes. Logs `JML [leaver] user deleted: userName=X, tokens_revoked=N`

#### `backend/oauth-oidc/src/main/java/com/iam/oauth/service/TokenService.java`
Added `revokeAllTokensForUser(String subject)`:
- Calls `tokenRepo.revokeAllBySubject(subject)`
- Returns count of revoked tokens

#### `backend/scim/src/test/java/com/iam/scim/ScimUserServiceTest.java`
- Updated constructor to accept `TokenService` mock
- Updated `deleteUser_found_deletes` to verify `tokenService.revokeAllTokensForUser()` is called

---

## Configuration

### `backend/api-gateway/src/main/resources/application.properties`
```properties
saml.sp.entity-id=http://localhost:8080/saml/sp
saml.sp.acs-url=http://localhost:8080/saml/acs
saml.sp.base-url=http://localhost:8080
saml.idp.sso-url=https://idp.example.com/sso
saml.idp.signing-cert=MIID...  # IdP public cert for assertion validation
```

---

## Key Technical Decisions

### Why JSR 105 instead of OpenSAML signing?
OpenSAML 4.0.0's signing APIs are poorly structured. JSR 105 (JDK XML Digital Signature) with a `PrivateKey` from the existing keystore is simpler and more reliable. Same RSA-SHA256 algorithm, same key material.

### Why inclusive canonical XML?
Exclusive canonical XML (`exc-c14n#with-comments`) is not available in the JDK's XML Digital Signature implementation. Inclusive canonical XML (`INCLUSIVE_WITH_COMMENTS`) is widely interoperable and works with all major IdPs.

### Why in-memory request ID store?
Redis was planned in the original spec, but the request ID TTL is only 5 minutes and the volume is low (per-SSO-session). An in-memory `ConcurrentHashMap` with a scheduled cleanup task is simpler and sufficient.

### Why SAML tokens as query params?
OIDC uses fragments (`#access_token=...`) for implicit/hybrid flows. SAML 2.0 §3.4.4 specifies query parameters for the redirect binding. Since we're bridging to OAuth, query params maintain SAML convention.
