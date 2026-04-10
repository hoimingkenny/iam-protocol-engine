---
title: Phase 7 — SAML 2.0 SP
sidebar_position: 1
description: What Phase 7 builds — SAML 2.0 Service Provider with SP-initiated SSO, ACS, and SAML→OIDC Bridge.
---

# Phase 7 — SAML 2.0 SP

## What Was Built

Phase 7 implements a SAML 2.0 Service Provider (SP). Unlike the OAuth/OIDC flow where this server is the authorization authority, here it acts as a SP that initiates SSO with an external Identity Provider (IdP). After the IdP authenticates the user and returns a SAML assertion, this server validates it and issues standard OIDC tokens.

## Architecture

```
Browser                    IAM SP (this server)              IdP
  │                               │                           │
  │  GET /saml/initiate          │                           │
  │─────────────────────────────>│                           │
  │                              │  302 SAMLRequest           │
  │                              │───────────────────────────>│
  │                              │    (AuthnRequest signed)   │
  │                              │                           │
  │  302 (redirect to IdP)       │                           │
  │<─────────────────────────────│                           │
  │                              │                           │
  │  [User authenticates]        │                           │
  │                              │                           │
  │  POST /saml/acs              │                           │
  │  (SAMLResponse)              │                           │
  │─────────────────────────────>│                           │
  │                              │  Validate assertion        │
  │                              │  Issue OIDC tokens         │
  │                              │                           │
  │  302 redirect_uri+tokens     │                           │
  │<─────────────────────────────│                           │
```

## Protocol Flow (SP-Initiated SSO)

1. **Initiate:** Browser → `GET /saml/initiate?client_id=...&redirect_uri=...`
2. **AuthnRequest:** SP builds and signs a `AuthnRequest` XML document, redirects to IdP SSO URL
3. **IdP Auth:** IdP authenticates user, returns `AuthnRequest` via HTTP-POST binding
4. **ACS:** SP validates SAML assertion at `POST /saml/acs`
5. **Bridge:** SP maps SAML NameID → OAuth `sub`, issues access_token + refresh_token + id_token
6. **Redirect:** Browser redirected to `redirect_uri?access_token=...&id_token=...`

## Key Files

| File | Role |
|------|------|
| `SamlMetadataService` | Generates signed SP metadata XML |
| `SamlAuthnRequestService` | Builds and signs AuthnRequest |
| `SamlAssertionValidator` | Validates SAMLResponse (7-step) |
| `SamlAcsController` | `POST /saml/acs` endpoint |
| `SamlToOidcTokenService` | Maps SAML NameID → OIDC tokens |
| `JwksService` | Provides signing keypair for SAML signatures |

## Security Properties

- **XML signatures:** RSA-SHA256 (JSR 105, not OpenSAML signing APIs)
- **Inclusive canonical XML:** C14N (JDK default, not exclusive)
- **Replay protection:** Request ID stored in-memory with 5-min TTL
- **Timing validation:** NotBefore/NotOnOrAfter checked against system time
- **Audience restriction:** SP entityID must match
- **PKCE note:** SAML does not use PKCE; signature provides equivalent assurance

## Standards

- [SAML 2.0](https://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf) — OASIS standard
- [RFC 7523](https://www.rfc-editor.org/rfc/rfc7523) — SAML 2.0 Profile for OAuth 2.0 Client Authentication
