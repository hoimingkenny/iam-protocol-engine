---
title: Specification
sidebar_position: 3
description: The canonical specification for IAM Protocol Engine — module responsibilities, data model, API design, code style, and success criteria.
---

# IAM Protocol Engine — Specification

> This spec is the source of truth. All implementation must trace back to these sections.

## Objective

Build a portfolio-grade enterprise IAM platform that demonstrates RFC-level protocol knowledge:

- OAuth 2.0 (Auth Code + PKCE, Client Credentials)
- OIDC (ID token, discovery, JWKS, userinfo)
- SAML 2.0 (SP-initiated SSO, SAML → OIDC bridge)
- SCIM 2.0 (User/Group CRUD, joiner/mover/leaver)
- WebAuthn + TOTP MFA
- Device Authorization Grant (RFC 8628)

**Stack:** Java 21, Spring Boot 3.3, Spring Authorization Server 1.3 (scaffolding only), React 19 + Vite + MUI, PostgreSQL 16, Redis 7, Docker Compose.

---

## Module Responsibilities

| Module | Responsibility |
|--------|----------------|
| `auth-core` | JPA entities (OAuthClient, Token, ScimUser, WebAuthnCredential, TotpCredential, DeviceCode, AuditEvent). AuditService interface. Base repository. |
| `oauth-oidc` | `/authorize`, `/token` (auth code, client credentials, refresh), `/introspect`, `/revoke`, OIDC discovery, JWKS, ID token, `/userinfo` |
| `saml-federation` | SP metadata, AuthnRequest, ACS, assertion validation, SAML→OIDC claim mapping |
| `scim` | `/scim/v2/Users`, `/scim/v2/Groups` — full CRUD + joiner/mover/leaver |
| `mfa` | TOTP enrollment + verification, WebAuthn registration + assertion |
| `device-flow` | `/device_authorization`, `/device` approval page, `/token` device grant exchange |
| `demo-resource` | Protected REST API that validates access tokens |
| `api-gateway` | Spring Boot entry point, wires all modules together |

---

## Code Style

**Java naming:** Standard Java conventions (`camelCase` methods, `PascalCase` classes, `SCREAMING_SNAKE_CASE` constants).

**Spring endpoints follow RFC naming:** `authorization_code`, `refresh_token`, `client_credentials` — match RFC 6749/7636/8628 token types exactly.

**Error responses:** RFC-compliant OAuth error format:

```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri mismatch"
}
```

**ID token claims (minimum):** `iss`, `sub`, `aud`, `exp`, `iat`, `nonce`

**Structured logging fields always present:** `client_id`, `sub`, `scope`, `jti`, `event_type`

---

## Boundaries

**Always:**

- RS256 signing; `kid` in JWKS from day one
- PKCE required for public clients
- `redirect_uri` exact match (no pattern matching)
- Structured audit logs with `client_id`, `sub`, `jti`
- Flyway migrations; no raw SQL in application code
- Redis TTL for all short-lived state (auth codes, device codes, nonce)

**Never:**

- Commit secrets or real credentials to source control
- Use implicit flow or hybrid flow
- Use symmetric signing (HS256) for production tokens
- Bypass signature validation on SAML assertions
- Store TOTP secrets in plaintext

---

## Success Criteria

| ID | Criterion | Verification |
|----|-----------|--------------|
| SC-01 | Auth Code + PKCE flow completes end-to-end | Integration test with real /token endpoint |
| SC-02 | Client Credentials flow issues valid access token | `curl` against `/token` with `grant_type=client_credentials` |
| SC-03 | Refresh token rotation invalidates old token | Two sequential refresh calls; second invalidates first |
| SC-04 | `/introspect` returns `{"active":true}` for valid token | Integration test |
| SC-05 | OIDC discovery returns correct metadata | `curl /.well-known/openid-configuration` |
| SC-06 | JWKS endpoint contains valid RSA public key with `kid` | `curl /.well-known/jwks.json` |
| SC-07 | ID token is valid RS256 JWT with required claims | Decode and verify signature against JWKS |
| SC-08 | SCIM `/Users` CRUD returns RFC-compliant responses | Integration tests |
| SC-09 | SCIM joiner → token revocation → leaver flow works | E2E test |
| SC-10 | Device Authorization Grant completes (RFC 8628) | `curl` device flow sequence |
| SC-11 | WebAuthn registration creates stored credential | Integration test |
| SC-12 | WebAuthn assertion verification succeeds | Integration test |
| SC-13 | TOTP verification succeeds with correct code | Integration test |
| SC-14 | Protected demo API rejects requests without valid Bearer token | `curl /api/resource` without token → 401 |
| SC-15 | Docker Compose starts PostgreSQL + Redis | `docker compose up -d` |
