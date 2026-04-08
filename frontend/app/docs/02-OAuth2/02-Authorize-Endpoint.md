---
title: /authorize Endpoint
sidebar_position: 3
description: RFC 6749 §4.1 — how the authorization endpoint validates requests and issues authorization codes.
---

# /authorize Endpoint

## RFC 6749 §4.1 — What the Spec Says

The authorization endpoint receives a GET request with query parameters. It validates everything, then either redirects with an error or redirects with an authorization code.

```
Browser → GET /authorize?
  client_id=my-client&
  redirect_uri=https://app.example.com/callback&
  response_type=code&
  scope=openid&
  state=xyz123&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256

AS → Browser (302): https://app.example.com/callback?code=A1B2C3&state=xyz123
```

## Validation Order

The endpoint validates in this order (fail-fast on first error):

```
1. client_id present?
2. client_id exists in registry?
3. redirect_uri present and exactly matches registered URI?
4. response_type == "code"?  (only code is supported)
5. If public client: code_challenge present, code_challenge_method == "S256"?
6. All requested scopes allowed for this client?
```

## Validation Logic

```java
// 1. client_id required
if (clientId == null || clientId.isBlank())
    return redirectError("invalid_request", "client_id is required");

// 2. Client must exist
OAuthClient client = clientRepo.findByClientId(clientId).orElse(null);
if (client == null)
    return redirectError("invalid_client", "unknown client_id");

// 3. redirect_uri exact match (no pattern matching!)
Set<String> registered = parseCsv(client.getRedirectUris());
if (!registered.contains(redirectUri))
    return redirectError("invalid_request", "redirect_uri mismatch");

// 4. Only "code" response_type
if (!"code".equals(responseType))
    return redirectError("unsupported_response_type", "only 'code' is supported");

// 5. PKCE required for public clients
if (isPublic && (codeChallenge == null || !PkceUtils.isValidChallenge(codeChallenge)))
    return redirectError("invalid_request", "code_challenge required for public clients");
```

## redirect_uri Exact Match

This is a critical security constraint. Per RFC 6749:

> The authorization server MUST NOT redirect to a URI other than the one registered.

Pattern matching or substring matching is explicitly forbidden. `http://localhost:*/callback` and `https://app.example.com/callback` are different.

## Auth Code Issuance

On successful validation:

```java
String code = generateSecureCode();  // 32 random bytes, Base64URL encoded

AuthCode authCode = new AuthCode();
authCode.setCode(code);
authCode.setClientId(clientId);
authCode.setSubject(authenticatedUser);  // from login session (Phase 5)
authCode.setCodeChallenge(codeChallenge);
authCode.setCodeChallengeMethod(codeChallengeMethod);
authCode.setScope(scope);
authCode.setNonce(nonce);           // from OIDC request (Phase 3)
authCode.setExpiresAt(now().plus(5, MINUTES));
authCode.setConsumedAt(null);       // not yet used

authCodeRepo.save(authCode);
```

The auth code is:
- **Short-lived**: 5-minute TTL (RFC 6749 recommendation)
- **Single-use**: consumed_at set at token exchange, then rejected on replay
- **PKCE-bound**: challenge stored with the code, verified at `/token`

## Error Redirect Format

Errors are returned as redirect URI query parameters (RFC 6749 §4.1.2.1):

```
https://app.example.com/callback?
  error=invalid_request&
  error_description=redirect_uri+mismatch&
  state=xyz123
```

## Implementation: AuthorizeController

```java
@GetMapping("/oauth2/authorize")
public ResponseEntity<?> authorize(
    @RequestParam("client_id") String clientId,
    @RequestParam("redirect_uri") String redirectUri,
    @RequestParam("response_type") String responseType,
    @RequestParam(value="scope", required=false) String scope,
    @RequestParam(value="state", required=false) String state,
    @RequestParam(value="code_challenge", required=false) String codeChallenge,
    @RequestParam(value="code_challenge_method", required=false) String codeChallengeMethod,
    @RequestParam(value="subject", required=false) String subject  // Phase 5: from login session
) {
    AuthorizationRequest request = new AuthorizationRequest(
        clientId, redirectUri, responseType, scope, state,
        codeChallenge, codeChallengeMethod, isPublic
    );

    OAuthErrorResponse error = authorizeService.validateRequest(request);
    if (error != null) return errorToRedirect(error, redirectUri);

    if (subject == null || subject.isBlank())
        return errorToRedirect(
            OAuthErrorResponse.invalidRequest("subject required — login not yet integrated"),
            redirectUri
        );

    String redirectUrl = authorizeService.issueAuthCode(request, subject);
    return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
}
```

Note: `subject` is a query param in Phase 2 for testing convenience. Phase 5 replaces this with a real login session.
