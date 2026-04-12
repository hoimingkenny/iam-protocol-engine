---
title: Phase 2 — OAuth 2.0 Core
sidebar_position: 1
description: What Phase 2 built and how the pieces fit together.
---

import useBaseUrl from '@docusaurus/useBaseUrl';

# Phase 2 — OAuth 2.0 Core

## What Was Built

Phase 2 implements the core OAuth 2.0 flows from scratch — no Spring Authorization Server behavior, only the wire format. Every endpoint, token, and grant type is built by hand to RFC specification.

## Modules Changed

| Module | What Changed |
|--------|--------------|
| oauth-oidc | New: `AuthorizeService`, `TokenService`, `AuthorizeController`, `TokenController`, PKCE utilities, DTOs |
| demo-resource | New: `BearerTokenAuthenticationFilter`, `TokenValidationService`, `ResourceController`, `ResourceSecurityConfig` |

## Flows Implemented

```
# Auth Code + PKCE (RFC 6749 + RFC 7636)
GET  /oauth2/authorize  → validates client, redirect_uri, PKCE → issues auth code
POST /oauth2/token      → exchanges code + verifier → issues access + refresh tokens

# Client Credentials (RFC 6749 §4.2)
POST /oauth2/token      → validates client secret → issues access token only

# Refresh Token Rotation (RFC 6749 §6)
POST /oauth2/token      → validates old refresh → rotates both tokens

# Protected Resource (SC-14)
GET  /api/resource      → validates Bearer token → returns user identity
GET  /api/health        → public, no auth required
```

## OAuth 2.0 Authorization Code + PKCE Flow

<div className="diagram-card">
  <a href={useBaseUrl('/img/oauth2-pkce-flow.svg')} target="_blank" rel="noreferrer">
    <img
      src={useBaseUrl('/img/oauth2-pkce-flow.svg')}
      alt="OAuth 2.0 Authorization Code plus PKCE flow"
      style={{width: '100%', height: 'auto', display: 'block'}}
    />
  </a>

</div>

## Key Design Decisions

**Opaque bearer tokens in Phase 2.** Tokens are random strings (Base64URL-encoded 32 bytes) stored in the PostgreSQL `token` table. Phase 3 replaces these with RS256-signed JWTs.

**PKCE required for public clients, optional for confidential.** The `is_public` flag on `OAuthClient` controls whether a `code_verifier` is required at `/token` exchange.

**Refresh token rotation is atomic.** Old token is revoked (`revoked=true`) in the same transaction that issues the new pair. Replay of an old refresh token fails at `findByJtiAndRevokedFalse`.

## Test Coverage

```
oauth-oidc:
  PkceUtilsTest          19 tests  ← RFC 7636 Appendix B test vector
  AuthorizeServiceTest   11 tests  ← validation + auth code issuance
  TokenServiceTest       14 tests  ← all 3 grant types + rotation

demo-resource:
  ResourceControllerTest  7 tests  ← filter behavior + controller

Total: 51 new tests in Phase 2
```

## Commands

```bash
# Run Phase 2 tests only
./mvnw test -pl backend/oauth-oidc
./mvnw test -pl backend/demo-resource

# Run all backend tests
./mvnw test
```
