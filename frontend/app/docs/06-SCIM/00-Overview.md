---
title: Phase 6 — SCIM 2.0
sidebar_position: 1
description: What Phase 6 builds — SCIM 2.0 User and Group management, Joiner/Mover/Leaver lifecycle.
---

# Phase 6 — SCIM 2.0

## What Was Built

Phase 6 implements SCIM 2.0 (RFC 7643/7644) — the System for Cross-domain Identity Management protocol. It provides a structured way to manage users and groups in a directory, supporting the joiner/mover/leaver lifecycle common in enterprise identity management.

## Architecture

```
Client (e.g. Admin UI, SCIM-compatible IdP)
    │
    ├─ POST   /scim/v2/Users         → Create user  (joiner)
    ├─ GET    /scim/v2/Users         → List users
    ├─ GET    /scim/v2/Users/{id}    → Get user
    ├─ PUT    /scim/v2/Users/{id}    → Replace user (mover)
    ├─ DELETE /scim/v2/Users/{id}    → Delete user  (leaver)
    │
    ├─ POST   /scim/v2/Groups         → Create group
    ├─ GET    /scim/v2/Groups         → List groups
    ├─ GET    /scim/v2/Groups/{id}   → Get group
    ├─ PATCH  /scim/v2/Groups/{id}    → Add/remove members (RFC 7644 §4.3)
    └─ DELETE /scim/v2/Groups/{id}   → Delete group

All endpoints require Bearer token authentication.
```

## Modules Changed

| Module | What Changed |
|--------|--------------|
| `scim` | All new: DTOs, repositories, services, controllers (Tasks 18, 19) |
| `api-gateway` | Added `scim` as dependency; updated `@EnableJpaRepositories` |
| `oauth-oidc` | Added `RedisConfig` (provides `RedisTemplate<String, Object>`) |

## RFCs Implemented

- **RFC 7643** — SCIM Core Schema: User and Group resource types
- **RFC 7644** — SCIM Protocol: REST API, filtering, PATCH operations

## Flows Implemented

### User CRUD

```bash
# Create — joiner flow
POST /scim/v2/Users
Content-Type: application/json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "john.doe",
  "displayName": "John Doe",
  "emails": [{ "value": "john@example.com", "primary": true }],
  "active": true
}
→ 201 Created, Location: /scim/v2/Users/{uuid}

# Replace — mover flow
PUT /scim/v2/Users/{uuid}
→ Full resource replacement

# Delete — leaver flow
DELETE /scim/v2/Users/{uuid}
→ 204 No Content
```

### Group PATCH (RFC 7644 §4.3)

```bash
# Add members
PATCH /scim/v2/Groups/{id}
Content-Type: application/json
[{ "op": "add", "members": [{ "value": "user-uuid" }] }]

# Remove members
PATCH /scim/v2/Groups/{id}
Content-Type: application/json
[{ "op": "remove", "members": [{ "value": "user-uuid" }] }]
```

## Key Design Decisions

**Bearer token auth on all endpoints.** SCIM has no native auth mechanism — authentication is delegated to the hosting environment. All requests must carry a valid, non-revoked, non-expired Bearer token.

**Comma-separated members.** Group membership (`members` column on `ScimGroup`) is stored as a comma-separated string of UUIDs — consistent with the project's "no JSON arrays as column values" principle.

**Entities in auth-core, not scim.** The `ScimUser` and `ScimGroup` JPA entities live in `auth-core` because they represent the canonical identity store, not a SCIM-specific abstraction. The `scim` module provides the SCIM protocol layer.

**Filter parsing is simple.** The filter parser extracts the quoted string value (`"userName eq \"alice\""` → `alice`). Complex SCIM filters (AND, OR, NOT) are not implemented — only single-condition `userName eq "..."` and `displayName eq "..."`.

## Start the Backend

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL + Redis
./mvnw spring-boot:run -pl backend/api-gateway   # → http://localhost:8080
```

## Test with Client Credentials

```bash
# Get bearer token
TOKEN=$(curl -s -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=" \
  -d "grant_type=client_credentials&scope=openid profile email" | \
  jq -r '.accessToken')

# Create a user
curl -X POST http://localhost:8080/scim/v2/Users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],\
       "userName":"alice","displayName":"Alice Smith",\
       "emails":[{"value":"alice@example.com"}],"active":true}'
```
