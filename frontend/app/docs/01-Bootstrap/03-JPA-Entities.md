---
title: JPA Entity Design
sidebar_position: 4
description: The 10 JPA entities — schema decisions, PK strategy, lifecycle callbacks, and the critical patterns that keep the data model correct.
---

# JPA Entity Design

## The 10 Entities

| Entity | Primary Key | Description |
|--------|-------------|-------------|
| `OAuthClient` | `client_id` (String) | Client registry — id, secret hash, redirect URIs, scopes, grant types |
| `AuthCode` | `code` (String) | Short-lived auth codes with PKCE challenge |
| `Token` | `jti` (UUID) | Access/refresh/ID tokens — JTI, subject, expiry, revoked |
| `ScimUser` | `id` (UUID) | SCIM user — username, emails, phones, groups, JSONB attributes |
| `ScimGroup` | `id` (UUID) | SCIM group — display name, members, JSONB attributes |
| `WebAuthnCredential` | `credential_id` (String) | FIDO2 credential — transports, attestation type |
| `TotpCredential` | `id` (UUID) | TOTP secret — Base32 secret, digits, period, algorithm |
| `DeviceCode` | `device_code` (String) | RFC 8628 device flow — user code, status, polling count |
| `DirectoryLink` | `id` (UUID) | Hybrid identity — LDAP/Entra ID user linkage |
| `AuditEvent` | `id` (UUID) | Structured audit log — event type, actor, subject, JTI, IP |

## Primary Key Strategy

- **UUID for `Token`, SCIM, MFA, Directory, Audit** — PostgreSQL `gen_random_uuid()`, never guessable
- **String for `OAuthClient`, `AuthCode`, `DeviceCode`** — opaque tokens generated externally or as secure random strings, not auto-generated

## Schema Decisions

### Comma-Separated TEXT for Arrays

Array columns use comma-separated TEXT, not JSON columns:

```java
@Column(name = "redirect_uris", columnDefinition = "TEXT")
private String redirectUris;  // "https://app.example.com/cb,https://localhost:3000/cb"
```

Only `attributes` on SCIM entities uses JSONB — the SCIM spec requires flexible key-value pairs.

### Lifecycle Callbacks

Entities with both `created_at` and `updated_at` use both `@PrePersist` and `@PreUpdate`:

```java
@PrePersist
protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    this.updatedAt = Instant.now();
}
```

## Critical Repository Pattern: `clearAutomatically = true`

Every bulk `UPDATE` or `DELETE` query must include `clearAutomatically = true`:

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Token t SET t.revoked = true WHERE t.jti = :jti")
int revokeByJti(@Param("jti") String jti);
```

Without this, the persistence context is not cleared after the bulk operation. A subsequent `findByJtiAndRevokedFalse()` can return a stale cached entity with `revoked = false`, even though the database was updated.

## Named Parameter Queries

All bulk operations use `@Param` named parameters:

```java
@Modifying(clearAutomatically = true)
@Query("DELETE FROM Token t WHERE t.expiresAt < :now")
int deleteExpired(@Param("now") Instant now);
```

## The Schema: 10 Tables, 16 Indexes

`V1__init.sql` creates all tables with proper indexes:

```sql
CREATE TABLE oauth_client (
    client_id         VARCHAR(128) PRIMARY KEY,
    client_secret_hash VARCHAR(256) NOT NULL,
    client_name        VARCHAR(256) NOT NULL,
    redirect_uris      TEXT NOT NULL,
    scopes             TEXT,
    grant_types        TEXT NOT NULL,
    client_type        VARCHAR(32) NOT NULL,
    id_token_signed_response_alg VARCHAR(16) NOT NULL DEFAULT 'RS256',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_subject ON token(subject);
CREATE INDEX idx_token_jti ON token(jti);
CREATE INDEX idx_auth_code_expires ON auth_code(expires_at);
CREATE INDEX idx_device_code_status ON device_code(status);
-- ... 16 indexes total
```
