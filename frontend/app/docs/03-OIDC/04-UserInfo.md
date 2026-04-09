---
title: /userinfo Endpoint
sidebar_position: 5
description: OIDC Core 1.0 §5.3 — fetching additional user claims.
---

# /userinfo Endpoint

## What It Is

The UserInfo endpoint is an OAuth 2.0 protected resource that returns additional claims about the authenticated user. It's the second place OIDC exposes user data — the first being the ID token's claims.

```
GET /userinfo
Authorization: Bearer <access_token>
```

## Why Both ID Token and UserInfo?

The ID token is a JWT — compact, signed, verifiable offline. But it has to stay small (tokens are sent on every request), and its claims are limited to the essentials.

UserInfo is a standard REST endpoint. It can return richer, human-readable data:

```
# ID token claims (small, signed, always available):
{ "sub": "user-001", "iss": "...", "aud": "...", "iat": ..., "exp": ... }

# UserInfo response ( richer, optional):
{ "sub": "user-001", "name": "Alice Smith", "email": "alice@example.com" }
```

The ID token proves *who* the user is. UserInfo provides *what* you know about them.

## Request

```
GET /userinfo
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

The access token is the same one returned at `/token`. It must be a valid, non-expired token associated with an authorization code or client credentials grant.

## Response

```json
{
  "sub": "user-001",
  "name": "user-001",
  "scope": "openid profile email"
}
```

**Always present:** `sub` — the user identifier, must match the ID token's `sub`.

**Scope-driven:** `name`, `email`, and `profile` claims are only returned if the corresponding scopes were requested and granted.

## Claims and Scopes

| Scope | Claims returned |
|-------|-----------------|
| `openid` | `sub` (always) |
| `profile` | `name` |
| `email` | `email` (future) |

The `scope` claim in the response lists which scopes were used to generate this response — this tells the client which claims to expect.

## Error Responses

```
401 Unauthorized — missing or invalid Bearer token
```

No `WWW-Authenticate` header is required (unlike OAuth 2.0 token introspection, which does require it per RFC 7662).

## How UserInfo Fits Into the Full Flow

```
1. Client → GET /authorize    (with openid, profile, email scopes)
2. User authenticates
3. Client ← 302 with auth code
4. Client → POST /token       (code + verifier)
5. Client ← { access_token, id_token, refresh_token }
6. Client verifies id_token signature against JWKS ✓
7. Client → GET /userinfo     (Bearer access_token)
8. Client ← { sub, name, email, scope }
```

Steps 6 and 8 can happen in any order or in parallel — UserInfo is independent of ID token verification.

## Phase 3 Implementation Notes

In Phase 3, UserInfo retrieves claims from the access token's stored metadata (`sub` from the token's subject, `scope` from the token's scope field). User attributes like `name` and `email` are **not yet populated** from a user store — they return placeholder values or null.

Phase 5 (User Management) will integrate SCIM user storage, at which point UserInfo will look up actual user attributes from the database.

## OpenID Connect and GDPR

One subtle consideration: the `sub` claim in OIDC is a pairwise identifier by default — the server issues a different `sub` to each client so clients cannot correlate users across different relying parties. This is a privacy feature relevant to GDPR compliance.

## Verification

```bash
# Get an access token via Auth Code + PKCE
ACCESS_TOKEN="<your access token>"

# Fetch user info
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/userinfo | jq .

# Without a token — should be 401
curl http://localhost:8080/userinfo
# HTTP 401
```
