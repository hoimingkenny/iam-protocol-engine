---
title: /token Endpoint
sidebar_position: 4
description: RFC 6749 §3.2 — how the token endpoint handles authorization_code, client_credentials, and refresh_token grants.
---

# /token Endpoint

## What the Spec Requires

The token endpoint is a POST endpoint. All parameters are `application/x-www-form-urlencoded`. The AS authenticates the client, then issues tokens.

```
Client → POST /oauth2/token
  Content-Type: application/x-www-form-urlencoded

  grant_type=authorization_code&
  code=A1B2C3&
  redirect_uri=https://app.example.com/callback&
  client_id=my-client&
  code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

AS → Client:
  HTTP/1.1 200 OK
  {
    "access_token": "eyJhbG...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "rt_abc123"
  }
```

## Grant Type Routing

```java
public TokenResponse handleTokenRequest(TokenRequest request) {
    if (request.isAuthorizationCodeGrant()) {
        return handleAuthorizationCodeGrant(request);
    } else if (request.isClientCredentialsGrant()) {
        return handleClientCredentialsGrant(request);
    } else if (request.isRefreshTokenGrant()) {
        return handleRefreshTokenGrant(request);
    } else {
        return TokenResponse.error("unsupported_grant_type", "...");
    }
}
```

## Authorization Code Grant — Full Flow

### Step 1: Validate code presence

```java
if (code == null || code.isBlank())
    return TokenResponse.error("invalid_request", "code is required");
```

### Step 2: Find unused auth code

```java
Optional<AuthCode> optCode = authCodeRepo.findByCodeAndConsumedAtIsNull(code);
if (optCode.isEmpty())
    return TokenResponse.error("invalid_grant", "code is invalid or expired");
AuthCode authCode = optCode.get();
```

`findByCodeAndConsumedAtIsNull` atomically finds AND ensures the code hasn't been consumed. Race condition: two concurrent `/token` requests with the same code — only one succeeds.

### Step 3: Verify client_id matches

```java
if (!authCode.getClientId().equals(request.clientId()))
    return TokenResponse.error("invalid_grant", "client_id mismatch");
```

### Step 4: PKCE verification (if required)

```java
private boolean verifyPkce(AuthCode authCode, String codeVerifier, String clientId) {
    OAuthClient client = clientRepo.findByClientId(clientId).orElse(null);
    if (client == null) return false;

    if (client.getIsPublic()) {
        // PKCE required — verifier MUST be present and match
        if (codeVerifier == null) return false;
        return PkceUtils.verifyCodeChallenge(codeVerifier,
            authCode.getCodeChallenge(), authCode.getCodeChallengeMethod());
    } else {
        // PKCE optional for confidential — if provided, verify; if absent, allow
        if (codeVerifier == null) return true;
        return PkceUtils.verifyCodeChallenge(codeVerifier,
            authCode.getCodeChallenge(), authCode.getCodeChallengeMethod());
    }
}
```

### Step 5: Consume the auth code

```java
authCode.setConsumedAt(Instant.now());
authCodeRepo.save(authCode);
```

Setting `consumedAt` (instead of deleting) preserves the audit trail.

### Step 6: Issue tokens

```java
Token accessToken = createToken(clientId, subject, scope, access_token, 1hour);
Token refreshToken = createToken(clientId, subject, scope, refresh_token, 7days);
tokenRepo.save(accessToken);
tokenRepo.save(refreshToken);
return TokenResponse.success(accessToken.getJti(), refreshToken.getJti(), 3600, scope);
```

## Client Credentials Grant — Machine-to-Machine

No user identity. No authorization code. No PKCE.

```java
private TokenResponse handleClientCredentialsGrant(TokenRequest request) {
    OAuthClient client = validateClientCredentials(request.clientId(), request.clientSecret());
    if (client == null) return TokenResponse.error("invalid_client", "...");

    // No refresh token for client_credentials (RFC 6749 §4.2.2)
    Token accessToken = createToken(clientId, "", scope, access_token, 1hour);
    tokenRepo.save(accessToken);
    return TokenResponse.accessTokenOnly(accessToken.getJti(), 3600, scope);
}
```

**Client secret hashing:** The secret is hashed with SHA-256 before storage. The same hash comparison is done at verification — the plaintext never touches the database.

## Refresh Token Rotation — RFC 6749 §6

Each refresh produces a new pair. The old refresh is revoked:

```java
Optional<Token> optOld = tokenRepo.findByJtiAndRevokedFalse(refreshToken);
if (optOld.isEmpty())
    return TokenResponse.error("invalid_grant", "refresh token invalid or expired");

Token oldRefresh = optOld.get();
oldRefresh.setRevoked(true);       // atomic with save
tokenRepo.save(oldRefresh);

Token newAccess = createToken(clientId, subject, scope, access_token, 1hour);
Token newRefresh = createToken(clientId, subject, scope, refresh_token, 7days);
tokenRepo.save(newAccess);
tokenRepo.save(newRefresh);
```

**Security property:** If an attacker steals the old refresh token and uses it before the legitimate client does, the legitimate client's use will find the token revoked and fail — alerting both the legitimate client and the AS to the attack.

## Token Response Format

```java
public record TokenResponse(
    String accessToken,
    String tokenType,     // always "Bearer" for access tokens
    Integer expiresIn,     // seconds until expiry
    String refreshToken,
    String idToken,        // Phase 3: populated for openid scope
    String scope,
    String error,
    String errorDescription
) {
    public static TokenResponse success(String at, String rt, int expiresIn, String scope) {
        return new TokenResponse(at, "Bearer", expiresIn, rt, null, scope, null, null);
    }
    public static TokenResponse accessTokenOnly(String at, int expiresIn, String scope) {
        return new TokenResponse(at, "Bearer", expiresIn, null, null, scope, null, null);
    }
    public static TokenResponse error(String e, String d) {
        return new TokenResponse(null, null, null, null, null, null, e, d);
    }
}
```

## Error Responses

RFC 6749 §5.2 specifies token error format:

```json
{
  "error": "invalid_grant",
  "error_description": "authorization code is invalid or expired"
}
```

Error codes: `invalid_request`, `invalid_client`, `invalid_grant`, `unauthorized_client`, `unsupported_grant_type`, `invalid_scope`.
