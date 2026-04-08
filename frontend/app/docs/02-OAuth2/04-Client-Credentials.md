---
title: Client Credentials Grant
sidebar_position: 5
description: RFC 6749 §4.2 — machine-to-machine authentication without a user context.
---

# Client Credentials Grant

## When to Use It

Client credentials is for **machine-to-machine** (M2M) communication. There is no user, no login, no authorization code — just a client authenticating as itself.

```
CI/CD System → POST /token (grant_type=client_credentials)
           ↓
Service Account / Robot User → access_token → protected API
```

Example use cases:
- A nightly cron job that accesses the reporting API
- A microservice calling another microservice's API
- A CI/CD pipeline pulling from a deployment API

## How It Differs From Auth Code

| Aspect | Authorization Code | Client Credentials |
|--------|-------------------|-------------------|
| Who authenticates | User (resource owner) + client | Client only |
| User identity in token | `subject` = user ID | `subject` = empty |
| Authorization step | `/authorize` redirect | None — direct POST |
| PKCE | Required for public clients | Not applicable |
| Refresh token | Yes | **No** (per RFC 6749) |

## Token Contents

Because there is no user, the token's `subject` claim is empty:

```json
{
  "access_token": "eyJhbG...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile"
}
```

The `scope` is the intersection of what the client is authorized for and what it requested.

## Why No Refresh Token?

RFC 6749 §4.2.1 explicitly states the authorization server MUST NOT issue a refresh token for client credentials. This is by design, not an oversight.

**The reason:** Refresh tokens exist to let a user re-authenticate without re-entering credentials. With M2M, there is no user — the client is authenticating as itself, every time, with its own credential (the `client_secret`). There is no "re-authenticating the user" step because there is no user.

When the access token expires, the client simply requests a new one directly — no rotation needed:

```
Access token expires
       ↓
Client re-authenticates with client_secret
       ↓
New access token issued
```

This is fundamentally different from auth code, where the refresh token lets the client get new tokens without redirecting the user back to the login page.

## Confidential vs Public Clients

**Confidential clients** have a `client_secret`. Authentication uses HTTP Basic (`client_id:client_secret` base64-encoded in the Authorization header) or sending both in the POST body.

**Public clients** (no secret — e.g., mobile apps, SPAs) cannot safely use client credentials because they cannot keep a secret. Use auth code + PKCE instead.

```bash
# Confidential: Basic auth
curl -X POST http://localhost:8080/oauth2/token \
  -H "Authorization: Basic bXktY2xpZW50OnRvcC1zZWNyZXQ=" \
  -d "grant_type=client_credentials&scope=openid"

# Confidential: body auth
curl -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=client_credentials" \
  -d "client_id=my-client" \
  -d "client_secret=my-secret" \
  -d "scope=openid"
```

## Database Client Registration

For Phase 2 testing, register a client directly in the DB:

```sql
INSERT INTO oauth_client (
  client_id,
  client_secret_hash,
  client_name,
  redirect_uris,
  allowed_scopes,
  grant_types,
  is_public
) VALUES (
  'my-machine-client',
  -- SHA-256 of 'my-secret' encoded as base64
  'REPLACE_WITH_GENERATED_HASH',
  'Machine Client',
  '',                    -- no redirect_uri needed for client_credentials
  'openid profile',
  'client_credentials',
  false                  -- confidential: has secret
);
```

Generate the secret hash:
```java
// In Java:
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest("my-secret".getBytes(UTF_8));
String base64 = Base64.getEncoder().encodeToString(hash);
```

## Scope Resolution

The scope in the token is resolved from the request (if provided) or falls back to the client's registered allowed scopes:

```java
private String resolveScope(String requested, String authorized) {
    if (requested != null && !requested.isBlank()) {
        return requested;  // client requested specific scope
    }
    return authorized != null ? authorized : "";
}
```

If the client requests a scope it isn't authorized for, the token service returns `invalid_scope` (unlike the authorize endpoint which validates upfront).
