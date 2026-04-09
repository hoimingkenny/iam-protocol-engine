package com.iam.oauth.controller;

import com.iam.authcore.entity.AuditEvent;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.AuditEventRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.authcore.repository.TokenRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin API endpoints for the Phase 5 Admin UI.
 *
 * GET /admin/clients          — list OAuth clients (paginated)
 * GET /admin/audit            — list audit events (paginated)
 * GET /admin/users            — list users (placeholder; SCIM in Phase 6)
 *
 * All endpoints require a valid Bearer token in the Authorization header.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final OAuthClientRepository clientRepo;
    private final AuditEventRepository auditRepo;
    private final TokenRepository tokenRepo;

    public AdminController(OAuthClientRepository clientRepo,
                           AuditEventRepository auditRepo,
                           TokenRepository tokenRepo) {
        this.clientRepo = clientRepo;
        this.auditRepo = auditRepo;
        this.tokenRepo = tokenRepo;
    }

    /**
     * Extract and validate Bearer token from Authorization header.
     *
     * @return the token value, or null if invalid
     */
    private Optional<Token> validateBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String tokenValue = authHeader.substring("Bearer ".length()).trim();
        if (tokenValue.isBlank()) {
            return Optional.empty();
        }
        return tokenRepo.findByJtiAndRevokedFalse(tokenValue)
            .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(java.time.Instant.now()));
    }

    private ResponseEntity<?> requireAuth(String authHeader) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized", "message", "Valid Bearer token required"));
        }
        return null; // OK
    }

    /**
     * List OAuth clients.
     *
     * GET /admin/clients?page=0&size=20
     */
    @GetMapping("/clients")
    public ResponseEntity<?> listClients(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Page<OAuthClient> clients = clientRepo.findAll(PageRequest.of(page, size));
        List<Map<String, Object>> items = clients.getContent().stream()
            .map(this::clientToMap)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "items", items,
            "total", clients.getTotalElements(),
            "page", clients.getNumber(),
            "pages", clients.getTotalPages()
        ));
    }

    /**
     * List audit events.
     *
     * GET /admin/audit?page=0&size=50
     */
    @GetMapping("/audit")
    public ResponseEntity<?> listAuditEvents(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "eventType", required = false) String eventType
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        Page<AuditEvent> events;
        if (eventType != null && !eventType.isBlank()) {
            events = auditRepo.findByEventTypeOrderByTimestampDesc(
                eventType, PageRequest.of(page, size));
        } else {
            events = auditRepo.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
        }

        List<Map<String, Object>> items = events.getContent().stream()
            .map(this::auditToMap)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "items", items,
            "total", events.getTotalElements(),
            "page", events.getNumber(),
            "pages", events.getTotalPages()
        ));
    }

    /**
     * List users (Phase 5 placeholder — SCIM in Phase 6).
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        ResponseEntity<?> authError = requireAuth(authHeader);
        if (authError != null) return authError;

        List<Map<String, Object>> users = List.of(
            Map.of("id", "1", "userName", "admin", "displayName", "Administrator", "active", true, "email", "admin@example.com"),
            Map.of("id", "2", "userName", "user1", "displayName", "User One", "active", true, "email", "user1@example.com"),
            Map.of("id", "3", "userName", "alice", "displayName", "Alice Smith", "active", true, "email", "alice@example.com")
        );
        return ResponseEntity.ok(Map.of("items", users, "total", users.size()));
    }

    private Map<String, Object> clientToMap(OAuthClient c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientId", c.getClientId());
        m.put("clientName", c.getClientName() != null ? c.getClientName() : c.getClientId());
        m.put("isPublic", c.getIsPublic());
        m.put("redirectUris", c.getRedirectUris());
        m.put("allowedScopes", c.getAllowedScopes());
        m.put("grantTypes", c.getGrantTypes());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> auditToMap(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("eventType", e.getEventType());
        m.put("actor", e.getActor());
        m.put("subject", e.getSubject());
        m.put("clientId", e.getClientId());
        m.put("scope", e.getScope());
        m.put("jti", e.getJti());
        m.put("ipAddress", e.getIpAddress());
        m.put("timestamp", e.getTimestamp());
        m.put("details", e.getDetails());
        return m;
    }
}
