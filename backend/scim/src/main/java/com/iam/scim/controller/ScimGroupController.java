package com.iam.scim.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import com.iam.scim.dto.ScimError;
import com.iam.scim.dto.ScimGroupDto;
import com.iam.scim.service.ScimGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * SCIM 2.0 Group endpoint per RFC 7644 §5.3.
 *
 * POST   /scim/v2/Groups        — create group
 * GET    /scim/v2/Groups        — list groups (?filter=, startIndex=, count=)
 * GET    /scim/v2/Groups/{id}  — get group
 * PATCH   /scim/v2/Groups/{id}  — modify group members (add/remove)
 * DELETE /scim/v2/Groups/{id}   — delete group
 *
 * Content-Type: application/scim+json
 */
@RestController
@RequestMapping("/scim/v2")
public class ScimGroupController {

    private final ScimGroupService groupService;
    private final TokenRepository tokenRepo;

    public ScimGroupController(ScimGroupService groupService, TokenRepository tokenRepo) {
        this.groupService = groupService;
        this.tokenRepo = tokenRepo;
    }

    private ResponseEntity<?> requireAuth(String authHeader) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ScimError.of(401, "Bearer token required"));
        }
        return null;
    }

    private Optional<Token> validateBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String tokenValue = authHeader.substring("Bearer ".length()).trim();
        if (tokenValue.isBlank()) return Optional.empty();
        return tokenRepo.findByJtiAndRevokedFalse(tokenValue)
            .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(Instant.now()));
    }

    @PostMapping(value = "/Groups",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = "application/scim+json")
    public ResponseEntity<?> createGroup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        ScimGroupDto dto = mapToGroupDto(body);
        Object result = groupService.createGroup(dto);

        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        ScimGroupService.CreateResult cr = (ScimGroupService.CreateResult) result;
        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.parseMediaType("application/scim+json"))
            .header("Location", cr.location())
            .body(cr.group());
    }

    @GetMapping(value = "/Groups",
               produces = "application/scim+json")
    public ResponseEntity<?> listGroups(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(value = "count", defaultValue = "10") int count
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = groupService.listGroups(filter, startIndex, count);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @GetMapping(value = "/Groups/{id}",
               produces = "application/scim+json")
    public ResponseEntity<?> getGroup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = groupService.getGroup(id);
        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @PatchMapping(value = "/Groups/{id}",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = "application/scim+json")
    public ResponseEntity<?> patchGroup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id,
            @RequestBody List<Map<String, Object>> operations
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = groupService.patchGroup(id, operations);
        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @DeleteMapping("/Groups/{id}")
    public ResponseEntity<?> deleteGroup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = groupService.deleteGroup(id);
        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private ScimGroupDto mapToGroupDto(Map<String, Object> body) {
        String displayName = (String) body.get("displayName");
        String externalId = (String) body.get("externalId");
        Map<String, Object> attributes = (Map<String, Object>) body.get("attributes");
        return new ScimGroupDto(null, displayName, null, null, externalId, attributes);
    }
}
