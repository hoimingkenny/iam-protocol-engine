---
title: Phase 9 Overview
sidebar_position: 1
description: Phase 9 Demo Hardening — end-to-end demo script, architecture reference, and project readiness checklist.
---

# Phase 9 — Demo Hardening

**Status:** Complete ✅
**Branch:** `phase-9-develop` merged to `main`

Phase 9 is the final phase. It doesn't add new protocol implementations — instead it packages everything built in Phases 1–8 into a presentable, demonstrable state: an executable demo script, a complete architecture README, and a learning site map.

---

## What Was Built

| Task | What | File |
|------|------|------|
| Task 27 | End-to-end demo script — 12 sections, all protocols | `scripts/demo-e2e.sh` |
| Task 28 | Architecture README with Mermaid diagrams | `README.md` |
| Learning site chapters | 3 chapters documenting the demo and architecture | `frontend/app/docs/09-Demo-Hardening/` |

---

## Demo Readiness Checklist

Before presenting this project, verify each item:

- [ ] `docker compose -f infra/docker-compose.yml up -d` — PostgreSQL + Redis start cleanly
- [ ] `./mvnw flyway:migrate -pl backend/auth-core` — all 5 migrations apply (V1–V5)
- [ ] `./mvnw spring-boot:run -pl backend/api-gateway` — app starts on port 8080
- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://localhost:8080/.well-known/openid-configuration` → valid JSON
- [ ] `./scripts/demo-e2e.sh` — all 12 sections complete without manual intervention
- [ ] Learning site (`frontend/app/`) builds with `npm run build`

---

## Protocol Coverage

This project demonstrates all major enterprise IAM protocols:

| Protocol | RFC | Implementation Location |
|----------|-----|------------------------|
| OAuth 2.0 Auth Code + PKCE | RFC 6749, RFC 7636 | `backend/oauth-oidc/` |
| OAuth 2.0 Client Credentials | RFC 6749 §4.2 | `backend/oauth-oidc/` |
| Token Introspection | RFC 7662 | `backend/oauth-oidc/src/.../IntrospectionController.java` |
| Token Revocation | RFC 7009 | `backend/oauth-oidc/src/.../RevocationController.java` |
| OIDC Discovery | RFC 8414 | `backend/oauth-oidc/src/.../DiscoveryController.java` |
| JWKS + Key Rotation | RFC 7517 | `backend/oauth-oidc/src/.../JwksController.java` |
| ID Token (RS256 JWT) | OIDC Core | `backend/oauth-oidc/src/.../IdTokenGenerator.java` |
| /userinfo | OIDC Core §5.3 | `backend/oauth-oidc/src/.../UserInfoController.java` |
| SCIM /Users CRUD | RFC 7644 §5.2 | `backend/scim/src/.../ScimUserController.java` |
| SCIM /Groups CRUD | RFC 7644 §5.3 | `backend/scim/src/.../ScimGroupController.java` |
| SCIM JML → Token Revocation | RFC 7644 | `backend/scim/src/.../ScimUserService.java` |
| SAML SP Metadata + AuthnRequest | SAML 2.0 | `backend/saml-federation/src/.../SamlController.java` |
| SAML ACS + Assertion Validation | SAML 2.0 | `backend/saml-federation/src/.../SamlAcsController.java` |
| SAML → OIDC Bridge | RFC 7523 | `backend/saml-federation/src/.../SamlToOidcTokenService.java` |
| TOTP MFA | RFC 6238 | `backend/mfa/src/.../TotpService.java` |
| WebAuthn / FIDO2 | W3C WebAuthn | `backend/mfa/src/.../WebAuthnService.java` |
| Device Authorization Grant | RFC 8628 | `backend/device-flow/src/.../DeviceFlowService.java` |

---

## Key Files

| Purpose | Path |
|---------|------|
| Demo script | `scripts/demo-e2e.sh` |
| Architecture README | `README.md` |
| Full specification | `SPEC.md` |
| Task tracker | `IMPLEMENTATION_PLAN.md` |
| Learning site | `frontend/app/docs/` |
| Database migrations | `backend/auth-core/src/main/resources/db/migration/` |
| Postman collection | `postman/iam-protocol-engine.json` |

---

## Related Phases

- **Phase 8** — Modern Auth: TOTP, WebAuthn, Device Flow (the MFA that Phase 9 demonstrates)
- **Phase 2** — OAuth 2.0 Core: the auth code + PKCE flow at the heart of the demo script
- **Phase 3** — OIDC Layer: discovery, JWKS, ID token (Sections 2, 7 of the demo script)
