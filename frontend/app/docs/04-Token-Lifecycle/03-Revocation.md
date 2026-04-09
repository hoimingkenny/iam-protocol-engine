---
title: Token Revocation
sidebar_position: 4
description: RFC 7009 — immediate token invalidation.
---

# Token Revocation

## What It Is

Token revocation (RFC 7009) lets a client tell the authorization server to invalidate a token immediately. Once revoked, the token cannot be used for any purpose — even if it hasn't expired yet.

Common use cases:
- User logs out of a client application
- Administrator revokes a compromised token
- Client application is uninstalled (refresh token revocation prevents continued access)
- Corporate policy requires immediate session termination

```
Client → POST /revoke { token: at_123 }
Server → (marks token revoked)
Client → GET /api/resource (Bearer at_123) → 401
```

## The Revocation Endpoint

```
POST /revoke
Content-Type: application/x-www-form-urlencoded

token        = <token_to_revoke>
token_type_hint = access_token   (optional: access_token or refresh_token)
```

## Response

```
HTTP 200 OK
```

Revocation always returns 200, even if:
- The token doesn't exist
- The token is already revoked

This is intentional per RFC 7009 §2.2 — the server must not indicate whether the token existed or was already revoked, to prevent information disclosure.

## Revocation and Introspection

After revocation, introspection confirms the token is inactive:

```
POST /revoke { token: at_123 } → 200 OK
POST /introspect { token: at_123 } → { "active": false }
```

## What Gets Revoked?

RFC 7009 §2.1: revocation invalidates the token presented. The server MAY also revoke related tokens (e.g., revoking an access token could implicitly revoke associated refresh tokens), but this is server-specific.

Our implementation:
- `POST /revoke { token: <access_token> }` → marks that access token revoked
- `POST /revoke { token: <refresh_token> }` → marks that refresh token revoked; associated access token remains valid until natural expiry

**Note:** For security, we do NOT automatically revoke the access token when its refresh token is revoked. This is a design choice — some deployments prefer the tighter guarantee of revoking both.

## Authorization

The revocation endpoint requires client authentication — the same as `/token`:
- `Authorization: Basic <client_id:client_secret>`
- or `client_id` + `client_secret` in the request body

This prevents one client from revoking another client's tokens.

## Revocation vs. Expiry

| | Revocation | Expiry |
|--|------------|--------|
| Trigger | Client request or admin action | Automatic at `exp` time |
| Timing | Immediate | Predictable, at a set time |
| Reversible | No | No |
| Visibility | Can be confirmed via introspection | Permanent |

## Why Return 200 Even for Non-Existent Tokens?

RFC 7009 §2.2 explicitly states:

> The authorization server responds with a successful HTTP status code even if the token was not present.

This prevents an attacker from using the revocation response to discover whether a token exists. A failed response would reveal that the token was valid, which is information that should remain private.

## Refresh Token Revocation on Logout

The typical logout flow:
```
1. Client → POST /revoke { token: <refresh_token> }
2. Client → POST /revoke { token: <access_token> }   (optional)
3. Client discards tokens locally
```

After step 1, even if the attacker steals the access token, it will expire within 1 hour. The refresh token is already invalid, so a full session takeover is impossible.

## Verification

```bash
# Revoke an access token
curl -X POST http://localhost:8080/revoke \
  -d "token=<ACCESS_TOKEN>" \
  -H "Authorization: Basic $(echo -n 'test-client:test-secret' | base64)"

# Confirm it's revoked
curl -X POST http://localhost:8080/introspect \
  -d "token=<ACCESS_TOKEN>" \
  -H "Authorization: Basic $(echo -n 'test-client:test-secret' | base64)"
# → {"active": false}
```
