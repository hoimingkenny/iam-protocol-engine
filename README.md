# IAM Protocol Engine

Portfolio-grade enterprise IAM platform demonstrating RFC-level protocol knowledge — built from scratch, not a Keycloak wrapper.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16, Redis 7, React 19 + Vite + MUI

**Implemented:** Phase 1 (Bootstrap) — skeleton, Docker Compose, JPA entities, repositories, API gateway entry point

---

## What This Project Is

Every endpoint, token, and assertion is deliberately built to demonstrate RFC-level understanding. The goal is protocol-level depth, not library usage.

| Protocol | RFC | Status |
|----------|-----|--------|
| OAuth 2.0 Authorization Code + PKCE | RFC 7636, 6749 | Phase 2 |
| OpenID Connect | RFC 9068 | Phase 2 |
| OAuth 2.0 Device Authorization Grant | RFC 8628 | Phase 3 |
| SAML 2.0 Federation | OASIS SAML 2.0 | Phase 4 |
| SCIM 2.0 | RFC 7644 | Phase 5 |
| TOTP (Google Authenticator) | RFC 6238 | Phase 6 |
| WebAuthn / FIDO2 | W3C WebAuthn L3 | Phase 6 |

---

## Prerequisites

- **Java 21** — [`sdk install java 21.0.3-tem`](https://sdkman.io/)
- **Docker & Docker Compose** — PostgreSQL 16 + Redis 7 run via `docker compose`
- **Maven 3.9+** — wrapped (`./mvnw`) or system-wide
- **PostgreSQL 16 client** (`psql`) — optional, for manual DB inspection

---

## Quick Start

### 1. Clone and set up environment

```bash
git clone <repo-url>
cd iam-protocol-engine
```

### 2. Start infrastructure

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml up -d
```

This starts **PostgreSQL 16** on `localhost:5432` and **Redis 7** on `localhost:6379`.

### 3. Run database migrations

```bash
./mvnw flyway:migrate -pl backend/auth-core
```

### 4. Build and verify

```bash
./mvnw clean install
./mvnw test
```

### 5. Run the application

```bash
./mvnw spring-boot:run -pl backend/api-gateway
```

The API gateway starts on **http://localhost:8080**. Actuator health: `curl http://localhost:8080/actuator/health`

---

## Architecture

```
backend/
├── auth-core/          ← JPA entities, repositories, AuditService, Flyway migrations
├── oauth-oidc/         ← /authorize, /token, OIDC discovery, JWKS (Phase 2)
├── saml-federation/    ← SAML SP, ACS, SAML→OIDC bridge (Phase 4)
├── scim/               ← SCIM 2.0 /Users and /Groups (Phase 5)
├── mfa/                ← TOTP + WebAuthn (Phase 6)
├── device-flow/        ← RFC 8628 Device Authorization Grant (Phase 3)
├── demo-resource/      ← Protected sample API (Phase 2)
└── api-gateway/        ← Single SpringBootApplication entry point
```

**`auth-core`** is the only module with JPA entities and repositories. All other modules depend on it.

### Data flow

```
Client → api-gateway → oauth-oidc → auth-core (entities/repos)
                              ↓
                        Redis (short-lived state: auth codes, device codes)
                              ↓
                        PostgreSQL (source of truth: clients, tokens, users)
```

---

## Key Commands

### Infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d     # Start
docker compose -f infra/docker-compose.yml down       # Stop
docker compose -f infra/docker-compose.yml logs -f   # Tail logs
```

### Database

```bash
./mvnw flyway:migrate -pl backend/auth-core          # Run migrations
./mvnw flyway:baseline -pl backend/auth-core         # Add baseline (if needed)
./mvnw flyway:clean -pl backend/auth-core             # Drop all tables (dev only)
```

### Building

```bash
./mvnw clean install                                  # Build all modules
./mvnw test                                           # Run all tests
./mvnw test -pl backend/auth-core                     # Test single module
./mvnw spring-boot:run -pl backend/api-gateway        # Run gateway
```

### Database inspection (with `psql`)

```bash
psql -h localhost -U iam_user -d iam_engine -c '\dt'         # List tables
psql -h localhost -U iam_user -d iam_engine -c '\d oauth_client'  # Describe table
```

---

## Schema

All tables live in the `public` schema of `iam_engine` (PostgreSQL):

| Table | Description |
|-------|-------------|
| `oauth_client` | Client registry — `client_id`, hashed secret, redirect URIs, scopes, grant types |
| `auth_code` | Short-lived authorization codes with PKCE challenge |
| `token` | Access / refresh / ID tokens — JTI, subject, expiry, revoked flag |
| `scim_user` | SCIM user — username, emails, phones, groups, attributes (JSONB) |
| `scim_group` | SCIM group — display name, members, attributes (JSONB) |
| `webauthn_credential` | FIDO2 credential — credential ID, transports, attestation type |
| `totp_credential` | TOTP secret — Base32 secret, digits, period, algorithm |
| `device_code` | RFC 8628 device flow — user code, status, polling count |
| `directory_link` | Hybrid identity — LDAP / Entra ID user linkage |
| `audit_event` | Structured audit log — event type, actor, subject, JTI, IP, details (JSONB) |

---

## Configuration

Environment variables (set in `infra/.env` for local development):

| Variable | Description |
|----------|-------------|
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `REDIS_PASSWORD` | Redis password |

**Never commit `.env`** — it's gitignored. Use `.env.example` as a template.

---

## Project Documentation

| Document | Description |
|----------|-------------|
| `SPEC.md` | Full project specification — 9 phases, API contracts, data model |
| `IMPLEMENTATION_PLAN.md` | 27-task breakdown across 9 phases with acceptance criteria |
| `doc/04. Phase 1 Code Change Summary.md` | Detailed change log for Phase 1 |

---

## Current Status

**Phase 1 (Bootstrap) — Complete**

- Maven multi-module skeleton (8 backend modules)
- Docker Compose (PostgreSQL 16 + Redis 7)
- JPA entities + repositories (10 entities, 10 repositories)
- `AuditService` interface
- API Gateway Spring Boot entry point
- Flyway migration (`V1__init.sql`) — 10 tables, 16 indexes
- Entity tests + application context test (16 tests passing)

**Next:** Phase 2 — OAuth 2.0 Core (PKCE utility, `/authorize`, `/token`, client credentials, `demo-resource`)

---

## Branches

```
main                  ← Stable (future)
phase1-task4-api-gateway  ← HEAD (Phase 1 complete)
phase1-bootstrap          ← Phase 1 parent branch
phase1-task1-pom-skeleton
phase1-task2-docker-flyway
phase1-task3-auth-core-entities
phase1-task4-api-gateway  ← current HEAD
```
