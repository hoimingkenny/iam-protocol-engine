---
title: SAML → OIDC Bridge
description: How SAML NameID is mapped to OAuth tokens after successful assertion validation.
---

# SAML → OIDC Bridge

## Why a Bridge?

SAML and OIDC are different protocols with different token formats. After validating a SAML assertion, the SP needs to issue tokens that downstream OAuth/OIDC clients can understand. The bridge maps SAML identity data into OIDC token claims.

## The Bridge Flow

```
SamlAssertionValidator
    │  (NameID extracted, all validations passed)
    ▼
SamlToOidcTokenService.bridge(nameId, sessionIndex, relayState, nonce)
    │
    ├─ Decode RelayState → { client_id, redirect_uri }
    │
    ├─ TokenService.issueTokensForSamlUser(nameId, clientId, nonce, scope)
    │   ├─ Create access_token  (opaque, RS256 signed JWT in Phase 3+)
    │   ├─ Create refresh_token (with family_id for rotation)
    │   ├─ Create id_token      (RS256 JWT with sub = SAML NameID)
    │   └─ Save all tokens to DB
    │
    └─ Build redirect URL with tokens as query params
```

## RelayState Decoding

When the SP initiated the AuthnRequest, it encoded `{"client_id":"...","redirect_uri":"..."}` as the RelayState. After validation, this is decoded to know which OAuth client to issue tokens for:

```java
// SamlToOidcTokenService.java
String decoded = new String(
    Base64.getUrlDecoder().decode(relayState), StandardCharsets.UTF_8);
JsonNode node = objectMapper.readTree(decoded);
String clientId = node.get("client_id").asText();
String redirectUri = node.get("redirect_uri").asText();
```

## Token Issuance

```java
// TokenService.issueTokensForSamlUser()
public TokenResponse issueTokensForSamlUser(String subject, String clientId,
                                            String nonce, String scope) {
    // Validate client exists
    OAuthClient client = clientRepo.findByClientId(clientId)
        .orElseThrow(() -> TokenResponse.error("invalid_client", "client not found"));

    // Resolve scope
    String resolvedScope = resolveScope(scope, client.getAllowedScopes());

    // Issue tokens with a new family_id
    String familyId = generateId();
    Token accessToken = createAccessToken(clientId, subject, resolvedScope, familyId);
    Token refreshToken = createRefreshToken(clientId, subject, resolvedScope, familyId);

    tokenRepo.save(accessToken);
    tokenRepo.save(refreshToken);

    // Issue ID token (OIDC Core 1.0 §3.1.3.3)
    String idToken = idTokenGenerator.generateIdToken(subject, clientId, nonce);

    return new TokenResponse(
        accessToken.getJti(), "Bearer", ACCESS_TOKEN_TTL_SECONDS,
        refreshToken.getJti(), idToken, resolvedScope, null, null
    );
}
```

## Claim Mapping

| SAML Element | OIDC Claim |
|---|---|
| `NameID` | `sub` |
| `SessionIndex` | (stored for logout, not in token) |
| `RelayState.client_id` | `aud` (in ID token) |
| (none) | `iss` = `http://localhost:8080` |
| (none) | `iat`, `exp` (standard JWT claims) |
| (none) | `nonce` (if provided in AuthnRequest) |

The SAML NameID becomes the OAuth `sub`. This means the same user logical identity works across both SAML SSO and regular OAuth/OIDC flows.

## Redirect with Tokens

After issuing tokens, the browser is redirected to the original `redirect_uri` with tokens as query parameters (not fragment, unlike OIDC implicit flow):

```
https://app.example.com/callback?
  access_token=eyJ...
  &token_type=Bearer
  &expires_in=3600
  &refresh_token=abc...
  &id_token=eyJ...
  &state=xyz
```

Query parameters are used because this is SAML's redirect binding convention (SAML 2.0 §3.4.4), not OIDC's fragment-based approach.

## Token Validation After SAML SSO

The issued tokens are fully valid OAuth/OIDC tokens:

```bash
# Introspect the access token
curl -X POST http://localhost:8080/oauth2/introspect \
  -d "token={{access_token}}"
# → { "active": true, "sub": "saml-nameid", "client_id": "test-client" }

# Use the access token
curl http://localhost:8080/api/resource \
  -H "Authorization: Bearer {{access_token}}"
# → 200 OK
```

The downstream OAuth infrastructure (introspection, revocation, resource validation) is identical regardless of whether the token originated from an OAuth auth code flow or a SAML SSO flow.
