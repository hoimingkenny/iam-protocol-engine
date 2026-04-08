package com.iam.demoresource.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Protected demo resource endpoints.
 *
 * SC-14: Validates that the demo API rejects unauthenticated requests.
 */
@RestController
@RequestMapping("/api")
public class ResourceController {

    /**
     * Returns the authenticated user's identity and granted scopes.
     * Demonstrates that the access token was successfully validated.
     */
    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> resource() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = auth != null ? (String) auth.getPrincipal() : null;
        return ResponseEntity.ok(Map.of(
            "message", "You accessed a protected resource",
            "subject", subject != null && !subject.isBlank() ? subject : "(client_credentials — no user subject)"
        ));
    }

    /**
     * Public health check — no authentication required.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
