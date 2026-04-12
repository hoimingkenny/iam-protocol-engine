# Phase 6 Code Change Summary

**Phase:** SCIM 2.0 — User and Group Management
**Branch:** `phase-6`
**RFCs:** RFC 7643 (Core Schema), RFC 7644 (SCIM Protocol)

---

## What Was Built

Phase 6 implements SCIM 2.0 (System for Cross-domain Identity Management) in a new `backend/scim/` module. It covers User CRUD (`/scim/v2/Users`) and Group CRUD (`/scim/v2/Groups`) with PATCH-based membership management per RFC 7644 §4.3.

## Modules Changed

| Module | Change |
|--------|--------|
| `backend/scim/` | New module: DTOs, repositories, services, controllers |
| `backend/api-gateway/pom.xml` | Added `scim` as dependency; updated `@EnableJpaRepositories` to include `com.iam.scim.repository` |
| `backend/oauth-oidc/` | Added `RedisConfig` providing `RedisTemplate<String, Object>` (pre-existing gap discovered during integration) |
| `backend/auth-core/` | Removed `ScimUserRepository` and `ScimGroupRepository` (duplicates — correct repos live in `scim` module) |

---

## Task 18: SCIM /Users CRUD

### Files Added

#### `backend/scim/src/main/java/com/iam/scim/dto/ScimUserDto.java` (new)
SCIM User wire format record per RFC 7643 §5.1. Nested `NameDto`, `EmailDto`, `MetaDto` records. Factory method `from()` maps entity → DTO. `parseEmails()` splits comma-separated string into `List<EmailDto>`.

#### `backend/scim/src/main/java/com/iam/scim/dto/ScimError.java` (new)
SCIM Error response record: `scimType`, `detail`, `status`. Static factories: `badRequest()`, `notFound()`, `conflict()`.

#### `backend/scim/src/main/java/com/iam/scim/dto/ScimListResponse.java` (new)
Generic list response wrapper: `totalResults`, `startIndex`, `itemsPerPage`, `resources`. `of()` factory.

#### `backend/scim/src/main/java/com/iam/scim/repository/ScimUserRepository.java` (new)
Spring Data JPA repository extending `JpaRepository<ScimUser, UUID>`. Custom queries: `findByUserName()`, `existsByUserName()`, `deleteByUserName()`, `findByUserNameContaining()` (case-insensitive substring match for filtering).

#### `backend/scim/src/main/java/com/iam/scim/service/ScimUserService.java` (new)
Business logic for all User operations:
- `createUser()` — validates userName, checks uniqueness, persists, returns `CreateResult(user, location, 201)` or `ScimError`
- `listUsers()` — pagination + optional filter via `findByUserNameContaining`
- `getUser()` — returns `ScimUserDto` or `ScimError.notFound`
- `replaceUser()` — full replace, checks userName uniqueness if changed
- `deleteUser()` — hard delete, returns null (→ 204)

#### `backend/scim/src/main/java/com/iam/scim/controller/ScimUserController.java` (new)
Spring REST controller. All endpoints require Bearer token auth (delegate to `TokenRepository.findByJtiAndRevokedFalse()` with expiry check). Content-Type: `application/json` (produces `application/scim+json`).

### Files Modified

#### `backend/api-gateway/pom.xml`
Added `scim` as dependency:
```xml
<dependency>
    <groupId>com.iam</groupId>
    <artifactId>scim</artifactId>
    <version>${project.version}</version>
</dependency>
```

#### `backend/api-gateway/src/main/java/com/iam/gateway/ApiGatewayApplication.java`
Updated `@EnableJpaRepositories` to scan both auth-core and scim repositories:
```java
@EnableJpaRepositories(basePackages = {"com.iam.authcore.repository", "com.iam.scim.repository"})
```

#### `backend/scim/pom.xml`
Added `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`.

### Files Deleted

#### `backend/auth-core/src/main/java/com/iam/authcore/repository/ScimUserRepository.java`
Removed duplicate repository (the authoritative one is now in `scim` module).

#### `backend/auth-core/src/test/java/com/iam/authcore/entity/ScimUserTest.java`
Removed test that depended on the deleted auth-core repository.

---

## Task 19: SCIM /Groups CRUD

### Files Added

#### `backend/scim/src/main/java/com/iam/scim/dto/ScimGroupDto.java` (new)
SCIM Group wire format record per RFC 7643 §5.2. Nested `MemberDto` with `value`, `$ref`, `type`. `from()` factory with overloaded variants for creating from entity + member UUIDs.

#### `backend/scim/src/main/java/com/iam/scim/repository/ScimGroupRepository.java` (new)
Spring Data JPA repository: `findByDisplayName()`, `existsByDisplayName()`, `findByDisplayNameContaining()` (case-insensitive substring match for filtering).

#### `backend/scim/src/main/java/com/iam/scim/service/ScimGroupService.java` (new)
Business logic for Group operations:
- `createGroup()` — validates displayName, persists with empty members
- `listGroups()` — pagination + optional filter
- `getGroup()` — returns group with parsed member list
- `patchGroup()` — RFC 7644 §4.3 PATCH: parses existing members into `Set<String>`, applies add/remove operations, validates all member IDs via `userRepo.existsById()`, persists as comma-separated string
- `deleteGroup()` — hard delete

#### `backend/scim/src/main/java/com/iam/scim/controller/ScimGroupController.java` (new)
REST controller with same Bearer auth pattern as `ScimUserController`.

### Files Deleted

#### `backend/auth-core/src/main/java/com/iam/authcore/repository/ScimGroupRepository.java`
Removed duplicate repository.

---

## Infrastructure Fixes Discovered During Integration

### Bug: Missing `RedisTemplate<String, Object>` (pre-existing gap)
**Symptom:** Application failed to start with `No qualifying bean of type 'RedisTemplate<String, Object>'` after adding scim module.

**Root Cause:** `LoginController` and `AuthorizeController` in `oauth-oidc` inject `RedisTemplate<String, Object>`. Spring Boot's `RedisAutoConfiguration` creates `RedisTemplate<Object, Object>` by default, not `RedisTemplate<String, Object>`.

**Fix:** Added `backend/oauth-oidc/src/main/java/com/iam/oauth/config/RedisConfig.java`:
```java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    // ...
    return template;
}
```

### Bug: Bean name collision on `ScimGroupRepository`
**Symptom:** `BeanDefinitionOverrideException: Invalid bean definition with name 'scimGroupRepository'`.

**Root Cause:** `auth-core` had `ScimGroupRepository` (with `findByDisplayName`, `findByExternalId`) and `scim` had its own `ScimGroupRepository` (with `findByDisplayNameContaining`). Both created beans named `scimGroupRepository`.

**Fix:** Deleted the auth-core duplicates. The `scim` module's repositories are the authoritative ones.

### Bug: `@PathVariable UUID id` runtime error
**Symptom:** `GET /scim/v2/Users/{id}` returned 500: `IllegalArgumentException: Name for argument of type UUID not specified`.

**Root Cause:** Spring couldn't determine the parameter name for `UUID id` at runtime (missing `-parameters` compiler flag for the scim module).

**Fix:** Added explicit `name` attribute: `@PathVariable(name = "id") UUID id` on all controller methods.

---

## Test Coverage

```
backend/scim/
  ScimUserServiceTest    7 tests   — createUser (3), getUser (2), deleteUser (2)
backend/auth-core/
  TokenTest              4 tests
  OAuthClientTest         3 tests
  AuditEventTest          3 tests
```

---

## API Verification

All endpoints verified via curl against running backend:

| Method | Path | Expected | Result |
|--------|------|----------|--------|
| POST | /scim/v2/Users | 201 + Location | ✓ |
| POST | /scim/v2/Users (duplicate) | 409 Conflict | ✓ |
| GET | /scim/v2/Users | 200 + list | ✓ |
| GET | /scim/v2/Users/{id} | 200 + user | ✓ |
| GET | /scim/v2/Users/{invalid} | 404 | ✓ |
| PUT | /scim/v2/Users/{id} | 200 + updated | ✓ |
| DELETE | /scim/v2/Users/{id} | 204 | ✓ |
| DELETE | /scim/v2/Users/{invalid} | 404 | ✓ |
| POST | /scim/v2/Groups | 201 + Location | ✓ |
| GET | /scim/v2/Groups | 200 + list | ✓ |
| GET | /scim/v2/Groups/{id} | 200 + group | ✓ |
| GET | /scim/v2/Groups/{invalid} | 404 | ✓ |
| PATCH | /scim/v2/Groups/{id} (add) | 200 + members | ✓ |
| PATCH | /scim/v2/Groups/{id} (remove) | 200 + empty members | ✓ |
| DELETE | /scim/v2/Groups/{id} | 204 | ✓ |
| GET | /scim/v2/Groups (no auth) | 401 | ✓ |
