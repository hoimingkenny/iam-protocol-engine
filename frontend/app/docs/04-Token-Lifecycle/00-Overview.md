---
title: Phase 4 — Token Lifecycle
sidebar_position: 1
description: What Phase 4 builds — refresh token rotation, token introspection, and revocation.
---

# Phase 4 — Token Lifecycle

## What Was Built

Phase 4 completes the token lifecycle: refresh token rotation, token introspection, and token revocation. By the end of Phase 4, every token can be queried, rotated, and revoked — completing the OAuth 2.0 token lifecycle.

## Modules Changed

| Module | What Changed |
|--------|--------------|
| `oauth-oidc` | `RefreshTokenService` (Task 13), `IntrospectionController` (Task 14), `RevocationController` (Task 14) |

## What Phase 4 Adds

**Refresh token rotation** prevents replay attacks. If a refresh token is stolen and used, the legitimate client's refresh token is revoked, causing the attack to fail on the second use.

**Token introspection** lets authorized clients (like the demo-resource) ask the authorization server whether a token is still valid — without parsing the token themselves.

**Token revocation** lets a client tell the authorization server to invalidate a token immediately.

## Flows Implemented

```
# Refresh Rotation (RFC 6749 §6)
POST /oauth2/token                    → new access_token + new refresh_token
  (old refresh_token is atomically revoked)

# Token Introspection (RFC 7662)
POST /introspect                     → {active: true/false, sub, scope, exp...}

# Token Revocation (RFC 7009)
POST /revoke                         → immediate token invalidation
```

## Key Design Decisions

**Atomic rotation.** The old refresh token is marked revoked in the same transaction that creates the new tokens. There is no window where both tokens are valid.

**Both active tokens revoked on abuse.** If a refresh token is reused (indicating theft), both the abused token and the newly-issued token from that exchange are revoked. This limits the blast radius.

**Introspection is a protected endpoint.** Only authorized clients can introspect tokens — not any anonymous caller.

**Revocation does not imply introspection.** A revoked token's existence can be confirmed via introspection (active=false), but revocation does not automatically trigger introspection.

## Test Coverage

```
oauth-oidc:
  RefreshTokenServiceTest   ~5 tests  ← rotation, reuse detection, client binding
  IntrospectionControllerTest ~4 tests ← active, expired, revoked tokens
  RevocationControllerTest  ~3 tests  ← immediate revocation
```
