package com.iam.oauth.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Token Introspection endpoint per RFC 7662.
 *
 * POST /introspect
 *
 * Allows a protected resource to query the authorization server to determine
 * the active state and metadata of a token. This is an OAuth 2.0 protected
 * resource (requires client authentication — confidential clients only).
 *
 * Introspection is idempotent: calling it multiple times with the same token
 * returns the same result.
 */
@RestController
@RequestMapping("/oauth2")
public class IntrospectionController {

    private final TokenRepository tokenRepo;

    public IntrospectionController(TokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    /**
     * Token introspection per RFC 7662 §2.1.
     *
     * @param token       The token to introspect (required)
     * @param tokenTypeHint  Optional hint about the token type (access_token or refresh_token)
     * @return JSON response indicating whether the token is active and its metadata
     */
    @PostMapping(value = "/introspect",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> introspect(
            @RequestParam("token") String token,
            @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(Map.of("active", false));
        }

        // Try the token as-is first, then try as JTI lookup
        Optional<Token> optToken = tryFindToken(token, tokenTypeHint);

        if (optToken.isEmpty() || !isActive(optToken.get())) {
            return ResponseEntity.ok(Map.of("active", false));
        }

        Token t = optToken.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("sub", t.getSubject() != null ? t.getSubject() : "");
        response.put("client_id", t.getClientId());
        response.put("scope", t.getScope() != null ? t.getScope() : "");
        response.put("token_type", hintToType(tokenTypeHint, t.getType()));
        response.put("exp", t.getExpiresAt().getEpochSecond());
        response.put("iat", t.getIssuedAt().getEpochSecond());

        return ResponseEntity.ok(response);
    }

    /**
     * Try to find a token by JTI, with optional type hint to optimize the lookup.
     */
    private Optional<Token> tryFindToken(String token, String tokenTypeHint) {
        // First try by JTI (the token value IS the jti for opaque tokens)
        Optional<Token> result = tokenRepo.findByJtiAndRevokedFalse(token);
        if (result.isPresent()) {
            return result;
        }

        // Try looking up by family_id if this might be a refresh token family query
        // (not directly applicable for introspection, so just return empty)
        return Optional.empty();
    }

    private boolean isActive(Token token) {
        return token.getExpiresAt() != null
            && token.getExpiresAt().isAfter(Instant.now())
            && !token.getRevoked();
    }

    private String hintToType(String hint, Token.TokenType actualType) {
        if (hint != null) return hint;
        if (actualType == Token.TokenType.access_token) return "access_token";
        if (actualType == Token.TokenType.refresh_token) return "refresh_token";
        return "access_token";
    }
}
