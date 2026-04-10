---
title: SCIM /Users CRUD
sidebar_position: 2
description: "RFC 7644 §5.2 — User resource type: create, list, get, replace, delete."
---

# SCIM /Users — RFC 7644 §5.2

## User Resource Type

The SCIM User resource (RFC 7643 §5.1) represents a user in the identity store. The implementation uses `ScimUserDto` as the wire format.

```java
// ScimUserDto fields
record ScimUserDto(
    String id,
    String userName,        // REQUIRED — unique identifier
    NameDto name,           // { givenName, familyName, formatted }
    String displayName,
    List<EmailDto> emails,  // { value, type, primary }
    Boolean active,
    String groups,          // comma-separated group IDs (not in DTO wire format)
    String externalId,
    Map<String, Object> attributes,  // JSONB — extension fields
    MetaDto meta,           // { resourceType, created, lastModified, location }
    String location         // full URL to this resource
) {}
```

## Endpoints

### POST /scim/v2/Users — Create User (Joiner Flow)

```
POST /scim/v2/Users
Content-Type: application/json
Authorization: Bearer <token>

{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "john.doe",
  "displayName": "John Doe",
  "emails": [{ "value": "john@example.com", "primary": true }],
  "active": true
}
→ 201 Created
→ Location: http://localhost:8080/scim/v2/Users/{uuid}
→ Body: ScimUserDto with id, createdAt, updatedAt
```

Validation:
- `userName` must be non-blank → 400 Bad Request
- `userName` must be unique → 409 Conflict

### GET /scim/v2/Users — List Users

```
GET /scim/v2/Users?filter=userName eq "john"&startIndex=1&count=10
Authorization: Bearer <token>

→ 200 OK
→ Body: ScimListResponse<ScimUserDto>
```

Supports filtering by `userName` (simple substring match, case-insensitive). Pagination via `startIndex` (1-based) and `count`.

### GET /scim/v2/Users/{id} — Get User

```
GET /scim/v2/Users/{uuid}
Authorization: Bearer <token>

→ 200 OK, Body: ScimUserDto
→ 404 Not Found if not found
```

### PUT /scim/v2/Users/{id} — Replace User (Mover Flow)

```
PUT /scim/v2/Users/{uuid}
Content-Type: application/json
Authorization: Bearer <token>

{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "john.doe.updated",
  "displayName": "John D.",
  "emails": [{ "value": "john.d@example.com" }],
  "active": false
}
→ 200 OK, Body: ScimUserDto (updated)
→ 404 Not Found if not found
→ 409 Conflict if new userName already taken
```

Replaces all mutable fields. The `id`, `createdAt`, and `location` are preserved.

### DELETE /scim/v2/Users/{id} — Delete User (Leaver Flow)

```
DELETE /scim/v2/Users/{uuid}
Authorization: Bearer <token>

→ 204 No Content on success
→ 404 Not Found if not found
```

Hard delete — the user record is permanently removed.

## Service Layer

`ScimUserService` handles all business logic:

```java
@Transactional
public Object createUser(ScimUserDto dto) {
    // 1. Validate userName
    if (dto.userName() == null || dto.userName().isBlank())
        return ScimError.badRequest("userName is required");

    // 2. Check uniqueness
    if (userRepo.existsByUserName(dto.userName()))
        return ScimError.conflict("userName already exists: " + dto.userName());

    // 3. Map DTO → entity
    ScimUser user = new ScimUser();
    user.setUserName(dto.userName());
    user.setDisplayName(dto.displayName() != null ? dto.displayName() : "");
    user.setEmails(dto.emails() != null && !dto.emails().isEmpty()
        ? dto.emails().get(0).value() : "");
    user.setActive(dto.active() != null ? dto.active() : true);
    // ...

    // 4. Persist and return
    ScimUser saved = userRepo.save(user);
    return new CreateResult(saved, BASE_LOCATION + "/" + saved.getId(), 201);
}
```

## Key Implementation Notes

**Email is single-value on entity.** The `ScimUser.emails` column is a single comma-separated string, not an array. The DTO maps `emails[0].value` to the column.

**`active` defaults to `true`.** If not specified, the user is created active.

**`userName` is the immutable unique identifier.** Changing `userName` via PUT returns 409 if the new name is taken by another user.
