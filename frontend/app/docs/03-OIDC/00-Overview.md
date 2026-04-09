---
title: Phase 3 — OIDC Layer
sidebar_position: 1
description: What Phase 3 built and how OIDC extends OAuth 2.0.
---

# Phase 3 — OIDC Layer

## What Was Built

Phase 3 adds the OpenID Connect identity layer on top of Phase 2's OAuth 2.0 foundation. It introduces four new capabilities: OIDC Discovery, JWKS, signed ID Tokens, and the UserInfo endpoint.

## Modules Changed

| Module | What Changed |
|--------|--------------|
| `oauth-oidc` | New: `DiscoveryController`, `JwksController`, `JwksService`, `RsaKeyPairGenerator`, `IdTokenGenerator`, `UserInfoController` |
| `api-gateway` | Added `iam.issuer` config property |

## What OIDC Adds Over OAuth 2.0

OAuth 2.0 answers: *"does the client have permission to access this resource?"*

OIDC (OpenID Connect) answers: *"who is the user, and what information can the client learn about them?"*

The identity layer consists of:
- **ID Token** — a signed JWT containing the user's identity claims (`sub`, `iss`, `aud`, `exp`, `iat`)
- **Discovery document** — a well-known JSON file describing the server's capabilities
- **JWKS endpoint** — the server's public keys so clients can verify ID token signatures
- **UserInfo endpoint** — a protected API to retrieve additional claims about the user

## Flows Implemented

```
# OIDC Discovery (RFC 8414)
GET /.well-known/openid-configuration  → returns server metadata

# JWKS — public keys for signature verification (RFC 7517)
GET /.well-known/jwks.json            → returns RSA public key(s) with kid

# Admin key rotation
POST /.well-known/jwks.json           → generates new key pair, returns new kid

# Auth Code flow now includes ID token (OIDC Core 1.0 §3.1)
GET  /oauth2/authorize                → same as Phase 2
POST /oauth2/token                    → returns access_token + refresh_token + id_token

# UserInfo — additional claims for authenticated user (OIDC Core 1.0 §5.3)
GET  /userinfo                        → returns sub, name, email, scope (Bearer required)
```

## Key Design Decisions

**RS256 from day one.** No symmetric (HS256) keys. Every token is signed with an RSA-2048 private key. The public key is published in JWKS.

**kid is the certificate thumbprint.** The key identity is the SHA-256 digest of the X.509 certificate, Base64-URL encoded without padding. This is stable across restarts (same key → same kid) so clients can cache JWKS responses.

**Key rotation without downtime.** A new key doesn't invalidate old keys — both the old and new public keys are served in JWKS. Existing tokens signed with the old key continue to validate. Only new tokens use the new key.

**Issuer is configurable.** `iam.issuer` in `application.properties` (or `IAM_ISSUER` env var) means the server can sit behind a reverse proxy (nginx, Cloudflare, ngrok) without token rejection.

## Test Coverage

```
oauth-oidc:
  PkceUtilsTest           19 tests  ← RFC 7636 Appendix B
  AuthorizeServiceTest    11 tests  ← validation + auth code
  TokenServiceTest        14 tests  ← all grants + ID token issuance

Total: 44 tests (Phase 2 baseline)
```

## Commands

```bash
# Run Phase 3 tests
./mvnw test -pl backend/oauth-oidc

# Start the server
./mvnw spring-boot:run -pl backend/api-gateway

# Verify all endpoints
curl http://localhost:8080/.well-known/openid-configuration | jq .
curl http://localhost:8080/.well-known/jwks.json | jq .
```
