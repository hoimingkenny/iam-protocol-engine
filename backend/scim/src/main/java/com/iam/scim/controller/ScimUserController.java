package com.iam.scim.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import com.iam.scim.dto.ScimError;
import com.iam.scim.dto.ScimListResponse;
import com.iam.scim.dto.ScimUserDto;
import com.iam.scim.service.ScimUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SCIM 2.0 User endpoint per RFC 7644 §5.2.
 *
 * POST   /scim/v2/Users        — create user
 * GET    /scim/v2/Users        — list users (?filter=, startIndex=, count=)
 * GET    /scim/v2/Users/{id}   — get user
 * PUT    /scim/v2/Users/{id}   — replace user
 * DELETE /scim/v2/Users/{id}   — delete user
 *
 * Content-Type: application/scim+json
 */
@RestController
@RequestMapping("/scim/v2")
public class ScimUserController {

    private final ScimUserService userService;
    private final TokenRepository tokenRepo;
    private final ObjectMapper objectMapper;

    public ScimUserController(ScimUserService userService,
                            TokenRepository tokenRepo,
                            ObjectMapper objectMapper) {
        this.userService = userService;
        this.tokenRepo = tokenRepo;
        this.objectMapper = objectMapper;
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

    @PostMapping(value = "/Users",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = "application/scim+json")
    public ResponseEntity<?> createUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        ScimUserDto dto = mapToUserDto(body);
        Object result = userService.createUser(dto);

        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        ScimUserService.CreateResult cr = (ScimUserService.CreateResult) result;
        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(org.springframework.http.MediaType.parseMediaType("application/scim+json"))
            .header("Location", cr.location())
            .body(cr.user());
    }

    @GetMapping(value = "/Users",
                produces = "application/scim+json")
    public ResponseEntity<?> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(value = "count", defaultValue = "10") int count
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = userService.listUsers(filter, startIndex, count);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @GetMapping(value = "/Users/{id}",
                produces = "application/scim+json")
    public ResponseEntity<?> getUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = userService.getUser(id);
        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @PutMapping(value = "/Users/{id}",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = "application/scim+json")
    public ResponseEntity<?> replaceUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        ScimUserDto dto = mapToUserDto(body);
        Object result = userService.replaceUser(id, dto);

        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType("application/scim+json"))
            .body(result);
    }

    @DeleteMapping("/Users/{id}")
    public ResponseEntity<?> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable(name = "id") UUID id
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Object result = userService.deleteUser(id);
        if (result instanceof ScimError err) {
            return ResponseEntity.status(err.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(err);
        }

        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private ScimUserDto mapToUserDto(Map<String, Object> body) {
        String userName = (String) body.get("userName");
        String displayName = (String) body.get("displayName");
        Boolean active = (Boolean) body.get("active");
        String externalId = (String) body.get("externalId");
        Map<String, Object> attributes = (Map<String, Object>) body.get("attributes");

        List<ScimUserDto.EmailDto> emails = null;
        Object emailsObj = body.get("emails");
        if (emailsObj instanceof List<?> list) {
            emails = list.stream()
                .filter(e -> e instanceof Map)
                .map(e -> {
                    Map<String, Object> m = (Map<String, Object>) e;
                    return new ScimUserDto.EmailDto(
                        (String) m.get("value"),
                        (String) m.get("type"),
                        (Boolean) m.get("primary")
                    );
                })
                .toList();
        }

        return new ScimUserDto(
            null, userName, null, displayName, emails, active, null, null, externalId, attributes
        );
    }
}
