---
title: Implementation Plan
sidebar_position: 4
description: 27-task breakdown across 9 phases with acceptance criteria, verification steps, and dependencies.
---

# IAM Protocol Engine — Implementation Plan

> Full task list with acceptance criteria and dependencies.

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Spring Authorization Server as scaffolding only | Core protocol logic written by hand — Spring handles wire-format, not behavior |
| RS256 + Nimbus JOSE+JWT | Standard, mature; `kid` in JWKS from day 1 for rotation |
| Redis for all short-lived state | Auth codes, nonce, device flow polling state, revocation cache |
| PostgreSQL as source of truth | Long-lived entities (clients, tokens, users) |
| SCIM built manually (no SDK) | Protocol depth; SDK would hide the spec |
| Keycloak as primary SAML IdP for testing | Fast local iteration; Entra ID as demo realism |

## Dependency Graph

```
Docker Compose (PostgreSQL + Redis)
    │
    ▼
Parent POM + Module POMs
    │
    ▼
auth-core (entities, AuditService, base repo)
    │
    ├──────────────────────────────┐
    ▼                              ▼
oauth-oidc                   demo-resource
    │                              │
    │                              ▼
    │                     Token validation filter
    │
    ▼
OIDC discovery + JWKS
    │
    ▼
Token lifecycle (refresh, revoke, introspect)
    │
    ▼
SCIM 2.0
    │
    ▼
SAML 2.0 + SAML→OIDC bridge
    │
    ▼
MFA (TOTP + WebAuthn) + Device Flow
    │
    ▼
Frontend Admin UI (can start after oauth-oidc)
```

## Phase Summary

| Phase | Focus | Tasks |
|-------|-------|-------|
| 1 | Bootstrap | 1–4 |
| 2 | OAuth 2.0 Core | 5–9 |
| 3 | OIDC Layer | 10–12 |
| 4 | Token Lifecycle | 13–14 |
| 5 | Admin UI | 15–17 |
| 6 | SCIM 2.0 | 18–19 |
| 7 | SAML 2.0 | 20–22 |
| 8 | Modern Auth | 23–25 |
| 9 | Demo Hardening | 26–27 |

---

## Parallelization Opportunities

| Tasks | Can run in parallel with |
|-------|--------------------------|
| Tasks 6–9 (OAuth core) | Tasks 15–17 (Frontend scaffold) — no dependency between them |
| Task 18 (SCIM Users) | Task 20 (SAML metadata) — no shared code |
| Task 23 (TOTP) | Task 24 (WebAuthn) — separate endpoints, no shared state |
| Task 25 (Device Flow) | Task 18 — `device-flow` module is isolated |
