# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IAM Protocol Engine is a portfolio-grade enterprise IAM platform demo that builds OAuth 2.0, OIDC, SAML 2.0, SCIM 2.0, WebAuthn, and TOTP protocols from scratch (not a Keycloak wrapper). The point is protocol-level understanding — every endpoint, token, and assertion is deliberately built to demonstrate RFC-level knowledge.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16, Redis 7, React 19 + Vite + MUI.

## Build Commands

```bash
# Infrastructure
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL + Redis

# Backend
./mvnw clean install                    # Build all modules
./mvnw test                             # Run all tests
./mvnw test -pl backend/oauth-oidc      # Test a single module
./mvnw spring-boot:run -pl backend/api-gateway  # Run the gateway (starts all modules)

# Flyway migrations (run after docker compose is up)
./mvnw flyway:migrate -pl backend/auth-core

# Frontend (not yet scaffolded)
cd frontend/app && npm install && npm run dev
```

## Architecture

### Module Layout

```
backend/
├── auth-core/          # JPA entities, AuditService interface, repositories
│   └── src/main/resources/db/migration/  # Flyway migrations
├── oauth-oidc/         # /authorize, /token, OIDC discovery, JWKS (protocols built by hand)
├── saml-federation/    # SAML SP, ACS, SAML→OIDC bridge
├── scim/               # SCIM 2.0 /Users and /Groups
├── mfa/                # TOTP + WebAuthn
├── device-flow/        # RFC 8628 Device Authorization Grant
├── demo-resource/      # Protected sample API (validates access tokens)
└── api-gateway/        # Single @SpringBootApplication entry point
```

`auth-core` is the only module with JPA entities and repositories. All other modules depend on it.

### Data Model

All entities live in `auth-core`. Key tables:
- `oauth_client` — client registry (client_id, hashed secret, redirect_uris, scopes, grant_types)
- `auth_code` — short-lived auth codes with PKCE code_challenge
- `token` — access/refresh/ID tokens (jti, type, subject, expiry, revoked)
- `scim_user`, `scim_group` — SCIM entities
- `webauthn_credential`, `totp_credential` — MFA
- `device_code` — RFC 8628 device flow
- `audit_event` — structured audit log

Comma-separated string columns are used for arrays (redirect_uris, scopes, groups) — NOT JSON columns. `attributes` on SCIM entities is JSONB.

### Critical Security Constraints

These are enforced across the codebase — do not weaken them:

- **PKCE is required** for public clients (S256 method only)
- **RS256 signing** — no HS256 symmetric keys for production tokens
- **`redirect_uri` exact match** — no pattern matching
- **`kid` in JWKS** from day one — key rotation is designed in
- **Refresh token rotation** — old token invalidated atomically on reuse
- **Structured audit logs** — always include `client_id`, `sub`, `scope`, `jti`, `event_type`
- **PostgreSQL for short-lived state** — auth codes, device codes, refresh tokens; Redis reserved for future caching needs

### Patterns

- Spring Authorization Server is used as **scaffolding only** — wire-format, not behavior. Protocol logic is written by hand.
- Redis is the short-lived state store; PostgreSQL is the source of truth.
- Flyway migrations in `auth-core/src/main/resources/db/migration/V1__init.sql` — no raw SQL in application code.
- OAuth grant types in endpoints match RFC 6749/7636/8628 naming exactly: `authorization_code`, `refresh_token`, `client_credentials`, `urn:ietf:params:oauth:grant-type:device_code`.

## Current Implementation State

Current branch: `main`. Phase 1–7 complete (Bootstrap, OAuth 2.0 Core, OIDC Layer, Token Lifecycle, Admin UI, SCIM 2.0, SAML 2.0 SP). Phase 8 (Modern Auth: TOTP + WebAuthn + Device Flow) is next. See `IMPLEMENTATION_PLAN.md` for full task status.

## Git Workflow

### Branch Strategy

Each phase has its own integration branch (`phase-N-develop`) branched from `main`. Task branches are created from `phase-N-develop` and merged back when done. When all tasks complete, `phase-N-develop` is merged into `main`.

```
main
  └── phase-7-develop  (branched from main when Phase 7 begins)
        ├── phase-7-task-20-saml-metadata
        ├── phase-7-task-21-acs
        ├── phase-7-task-22-saml-oidc-bridge
        └── phase-7-task-23-jml-lifecycle
```

### Branch Naming

- `phase-N-develop` — phase integration branch; branched from `main` at start of each phase
- `phase-N-task-XX-description` — individual task branch; branched from `phase-N-develop`; merged (squash or fast-forward) when done
- `phase-N` — historical phases used a flat branch-per-phase model; do not use this pattern

### Typical Phase Sequence
1. `git checkout -b phase-N-develop` from `main`
2. For each task: `git checkout -b phase-N-task-XX-description` from `phase-N-develop`
3. Implement, test, commit on the task branch
4. Merge task branch into `phase-N-develop` (squash or fast-forward — prefer squash for clean history)
5. Repeat for all tasks
6. When all tasks done: merge `phase-N-develop` into `main`
7. Update `IMPLEMENTATION_PLAN.md` checkpoint to mark phase complete

### Commits
Format:
```
<type>: <short description>

[Optional body explaining why]

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

Types: `Phase N Task X: <description>`, `docs:`, `fix:`, `Sync IMPLEMENTATION_PLAN.md:`

### Doc Files
- `doc/05. Testing Guide.md` — Postman-based API testing across all phases
- `doc/06. Phase 2 Code Change Summary.md`
- `doc/07. Phase 3 Code Change Summary.md`
- `doc/08. Phase 4 Code Change Summary.md`
- `doc/09. Phase 6 Code Change Summary.md`
- `doc/10. Phase 7 Code Change Summary.md`
- Learning site: `frontend/app/docs/` (Docusaurus, sidebar driven)

## Boundaries

**Ask before modifying:**
- Token lifetimes or new grant types
- Data model changes (new entity or column)
- External IdP configuration

**Never:**
- Commit secrets or credentials
- Use implicit/hybrid flow
- Bypass SAML signature validation
- Store TOTP secrets in plaintext
