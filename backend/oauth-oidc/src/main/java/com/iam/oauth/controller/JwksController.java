package com.iam.oauth.controller;

import com.iam.oauth.security.JwksService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JWKS endpoint per RFC 7517 §5.
 *
 * GET /.well-known/jwks.json
 *   Returns the server's current RSA public keys in JWKS format.
 *
 * POST /admin/keys/rotate (Task 10 — admin key rotation)
 *   Generates a new RSA key pair, adds it to the keystore, returns the new kid.
 *   Old keys remain valid for token validation.
 */
@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final JwksService jwksService;

    public JwksController(JwksService jwksService) {
        this.jwksService = jwksService;
    }

    /**
     * JWKS per RFC 7517 §5 — set of public keys used to verify RS256 signatures.
     */
    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return jwksService.buildJwksResponse();
    }

    /**
     * Admin endpoint: trigger key rotation.
     * Adds a new RSA key pair to the keystore. Returns the new kid.
     * The old key(s) remain valid for verifying existing tokens.
     */
    @PostMapping("/jwks.json")
    public Map<String, Object> rotateKey() {
        try {
            String newKid = jwksService.rotateKey();
            return Map.of(
                "status", "rotated",
                "new_kid", newKid,
                "message", "New key added. Old keys remain valid for token validation."
            );
        } catch (Exception e) {
            throw new IllegalStateException("Key rotation failed", e);
        }
    }
}
