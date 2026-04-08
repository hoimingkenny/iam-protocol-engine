package com.iam.oauth.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

/**
 * OIDC UserInfo endpoint per OIDC Core 1.0 §5.3.
 *
 * GET /userinfo
 *   Requires: valid Bearer token (Authorization header)
 *   Returns: OIDC standard claims (sub, name, email, profile, scope)
 *
 * Phase 3: returns claims from token store (sub + scope).
 * Future phases: look up name/email from SCIM user store based on token's subject.
 */
@RestController
public class UserInfoController {

    private final TokenRepository tokenRepo;

    public UserInfoController(TokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    /**
     * UserInfo endpoint per OIDC Core 1.0 §5.3.
     *
     * Returns claims for the authenticated user based on the bearer access token.
     * If the token is invalid or expired, returns 401.
     */
    @GetMapping(value = "/userinfo",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> userInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        String jti = authHeader.substring("Bearer ".length()).trim();
        Optional<Token> optToken = tokenRepo.findByJtiAndRevokedFalse(jti);

        if (optToken.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Token token = optToken.get();

        // Check expiry
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(401).build();
        }

        // Check it's an access token
        if (token.getType() != Token.TokenType.access_token) {
            return ResponseEntity.status(401).build();
        }

        String subject = token.getSubject() != null ? token.getSubject() : "";
        String scope = token.getScope() != null ? token.getScope() : "";
        Set<String> scopes = Set.of(scope.split("\\s+"));

        // Build response claims
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);  // OIDC Core §5.3: sub is REQUIRED

        // Scope-driven claims (OIDC Core §5.4: scope determines returned claims)
        if (scopes.contains("profile")) {
            claims.put("name", subject); // Placeholder: name lookup from SCIM in future phase
        }
        if (scopes.contains("email")) {
            // Placeholder: email lookup from SCIM in future phase
        }

        // Always include scope in response per OIDC
        claims.put("scope", scope);

        return ResponseEntity.ok(claims);
    }
}
