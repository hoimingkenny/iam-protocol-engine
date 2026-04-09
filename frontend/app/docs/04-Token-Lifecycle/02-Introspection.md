---
title: Token Introspection
sidebar_position: 3
description: RFC 7662 — how authorized clients query whether a token is still active.
---

# Token Introspection

## What It Is

Token introspection (RFC 7662) is a mechanism for an OAuth 2.0 authorization server to reveal metadata about a token to authorized clients. It answers the question: *"Is this token still valid?"*

This is necessary because:
- Access tokens in Phase 2 are **opaque** — they can't be parsed by the resource server
- The resource server (demo-resource) can't look inside the token to check expiry or revocation
- Instead, it calls `/introspect` to ask the authorization server

```
Client → GET /api/resource (Bearer at_123)
Resource Server → validation failed locally → returns 401

OR

Client → GET /api/resource (Bearer at_123)
Resource Server → POST /introspect {token: at_123}
Authorization Server → {active: true, sub: user-001, exp: 1234567890, ...}
Resource Server → 200 OK
```

## The Introspection Endpoint

```
POST /introspect
Content-Type: application/x-www-form-urlencoded

token        = <access_token>
token_type_hint = access_token   (optional hint about token type)
```

## Response

**Active token:**
```json
{
  "active": true,
  "sub": "user-001",
  "scope": "openid profile email",
  "client_id": "test-client",
  "token_type": "Bearer",
  "exp": 1775662439,
  "iat": 1775658839,
  "jti": "a1b2c3d4..."
}
```

**Inactive token (expired, revoked, or never issued):**
```json
{
  "active": false
}
```

## Active Token Fields

| Field | Description |
|-------|-------------|
| `active` | `true` if the token is currently valid |
| `sub` | Subject (user identifier) |
| `scope` | Space-separated list of granted scopes |
| `client_id` | Client that requested the token |
| `token_type` | Always `"Bearer"` for access tokens |
| `exp` | Expiration time (Unix timestamp) |
| `iat` | Issued-at time (Unix timestamp) |
| `jti` | JWT ID — unique identifier for this token |

Inactive tokens return only `{"active": false}` — no other fields.

## Why a Separate Endpoint?

OIDC provides an introspection mechanism (RFC 7662) that is distinct from OIDC's own introspection profile. The key difference: **OIDC defines its own token introspection endpoint** per OIDC Core 1.0 §5, while RFC 7662 is the more general OAuth 2.0 mechanism.

Our implementation uses RFC 7662 for both access and refresh tokens, since we have both types floating around.

## Introspection vs. JWKS

| | Introspection | JWKS |
|--|--------------|------|
| Purpose | "Is this token valid?" | "What key signed this token?" |
| Input | A token value | No input |
| Output | Token metadata (active, sub, exp...) | Public cryptographic keys |
| When | Every API call that needs validation | Once, to verify ID token signature |

The demo-resource uses introspection to validate access tokens on every request. Clients use JWKS once to verify ID token signatures.

## Authorization

Introspection is a **protected endpoint**. Only authorized clients can introspect tokens. The server authenticates the introspection request using the same mechanisms as `/token`:
- `Authorization: Basic <client_id:client_secret>`
- or `client_id` + `client_secret` in the request body

This prevents arbitrary clients from probing whether arbitrary tokens are valid.

## RFC 7662 §2.1 — Alternative Token Types

The introspection endpoint is designed to support any token type, not just access tokens. For refresh tokens, the response would include the additional fields relevant to refresh tokens (e.g., `refresh_token_type`). Our implementation currently handles both access and refresh tokens.

## Verification

```bash
# Active token
curl -X POST http://localhost:8080/introspect \
  -d "token=<ACCESS_TOKEN>" \
  -H "Authorization: Basic $(echo -n 'test-client:test-secret' | base64)" | jq .

# Expired or revoked token
curl -X POST http://localhost:8080/introspect \
  -d "token=<REVOKED_OR_EXPIRED_TOKEN>" \
  -H "Authorization: Basic $(echo -n 'test-client:test-secret' | base64)" | jq .
# → {"active": false}
```
