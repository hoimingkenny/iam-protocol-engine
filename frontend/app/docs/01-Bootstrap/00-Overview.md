---
title: Phase 1 Overview
sidebar_position: 1
description: What Phase 1 built — a complete bootstrap of the IAM Protocol Engine with Maven multi-module structure, Docker Compose infrastructure, JPA entities, and the Spring Boot API gateway entry point.
---

# Phase 1 — Bootstrap

**Status:** Complete
**Branch:** `phase1-task4-api-gateway`
**Lines changed:** ~2,159 lines across 49 files

Phase 1 built the foundation: a Maven multi-module project with 8 backend modules, Docker Compose for PostgreSQL + Redis, 10 JPA entities with Flyway migrations, and the Spring Boot API gateway entry point.

---

## What Was Built

| Task | What | Commit |
|------|------|--------|
| Task 1 | Maven multi-module skeleton — 8 backend modules, parent POM, Maven wrapper | `2eb952d` |
| Task 2 | Docker Compose + Flyway init — PostgreSQL 16, Redis 7, env-based secrets | `8c7f14f` |
| Task 3 | Auth Core Entities — 10 JPA entities, 10 repositories, AuditService, V1__init.sql | `63974d3` |
| Task 4 | API Gateway — single @SpringBootApplication entry point, actuator health | `d5c18d2` |
| Tests | Entity tests + application context test — 16 tests passing | `c270e35` |
| Review Fixes | TotpCredential updated_at NOT NULL fix, clearAutomatically cache fix | `ea79f13` |

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Flyway in `auth-core` only | JPA entities and schema live in `auth-core`; all other modules depend on it |
| Redis is short-lived state; PostgreSQL is source of truth | Auth codes (5min TTL), device codes (10min TTL), refresh tokens (7d TTL) in Redis; clients, tokens, users in PostgreSQL |
| `JPA @Query` for bulk UPDATE/DELETE with `@Modifying` | Standard Spring Data JPA; `clearAutomatically = true` is critical for cache consistency |
| H2 in-memory DB for unit tests | Fast, deterministic, no external dependency required |
| UUID as primary key for Token, SCIM entities, MFA | PostgreSQL native UUID via `gen_random_uuid()` |
| String PK for OAuthClient, AuthCode, DeviceCode | Opaque tokens generated externally or as secure random strings |
| JSONB only for SCIM `attributes` field | SCIM spec requires flexible attributes; other array columns use comma-separated TEXT |

---

## Critical Patterns Applied

### `@Modifying(clearAutomatically = true)`

All bulk `UPDATE`/`DELETE` queries include `clearAutomatically = true`. Without it, the persistence context is not cleared after a bulk operation, so subsequent `findByJtiAndRevokedFalse()` can return a stale cached entity.

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Token t SET t.revoked = true WHERE t.jti = :jti")
int revokeByJti(@Param("jti") String jti);
```

### `@PrePersist` / `@PreUpdate` Lifecycle Callbacks

Entities with `created_at` / `updated_at` columns use `@PrePersist` and `@PreUpdate` callbacks. This ensures timestamps are always set correctly at the application level, not the database level.

```java
@PrePersist
protected void onCreate() {
    this.createdAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    this.updatedAt = Instant.now();
}
```

### Named Parameter Queries with `@Param`

All bulk operations in repositories use named parameters with `@Param` annotations, not positional `?` placeholders.

```java
@Query("DELETE FROM Token t WHERE t.expiresAt < :now")
int deleteExpired(@Param("now") Instant now);
```

---

## Bugs Fixed During Phase 1

**1. `TotpCredential.updated_at` NOT NULL constraint violation**

`totp_credential.updated_at TIMESTAMPTZ NOT NULL` in the schema, but the entity had no `updatedAt` field or `@PreUpdate` callback. Any JPA update would throw `SQLException: NULL value in column "updated_at"`.

**Fix:** Added `updatedAt` field with `@Column(name = "updated_at", nullable = false)` and `@PreUpdate protected void onUpdate()`.

**2. Module `relativePath` error**

All 8 module POMs had `relativePath=..` pointing to `backend/pom.xml` instead of the root `pom.xml`.

**Fix:** Changed all to `relativePath=../../pom.xml`.

**3. `@Modifying` missing `clearAutomatically = true`**

Six bulk operations across `TokenRepository`, `AuthCodeRepository`, and `DeviceCodeRepository` were missing `clearAutomatically = true`, causing stale cache bugs after revocation or deletion.

---

## Known Issues

### macOS Docker Desktop — `password authentication failed`

Docker Desktop on macOS NATs `localhost:5432` connections through its VM. PostgreSQL sees source IPs from Docker's internal network (`172.17.0.x`), not `127.0.0.1`. The `pg_hba.conf` `127.0.0.1/32 trust` rule doesn't match, falling through to `scram-sha-256` auth.

**Fix:**
```bash
docker exec iam-postgres sed -i '$ a host all all 0.0.0.0/0 trust' /var/lib/postgresql/data/pg_hba.conf
docker restart iam-postgres
```

This is ephemeral — container restarts reset it. See [Docker Compose → macOS Docker Desktop](02-Docker-Compose.html#macos-docker-desktop--known-networking-issue) for full details.

### Flyway Maven command not configured

`./mvnw flyway:migrate` fails because the Flyway Maven plugin isn't wired into the POM. Tables are created via `V1__init.sql` which can be applied manually:

```bash
docker exec -i iam-postgres psql -U iam_user -d iam_engine < backend/auth-core/src/main/resources/db/migration/V1__init.sql
```

## Phase 2 Preview

Phase 2 (OAuth 2.0 Core) is next:

- **Task 5:** PKCE utility (`SecureRandom` verifier, SHA-256 challenge, Base64URL encoding)
- **Task 6:** `/authorize` endpoint — authorization code flow with PKCE validation
- **Task 7:** `/token` endpoint — code exchange, refresh token rotation
- **Task 8:** Client credentials grant
- **Task 9:** `demo-resource` — protected sample API that validates Bearer tokens
