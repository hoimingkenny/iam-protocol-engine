package com.iam.mfa.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import com.iam.mfa.service.WebAuthnService;
import com.iam.mfa.service.WebAuthnService.AuthenticationBeginResult;
import com.iam.mfa.service.WebAuthnService.RegistrationBeginResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * WebAuthn (W3C Web Authentication) endpoints.
 *
 * POST /webauthn/register/begin   — Generate registration challenge + options
 * POST /webauthn/register/complete — Verify attestation, store credential
 * POST /webauthn/authenticate/begin — Generate authentication challenge + options
 * POST /webauthn/authenticate/complete — Verify assertion, update sign_count
 */

@RestController
@RequestMapping("/webauthn")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final TokenRepository tokenRepo;
    private final ObjectMapper objectMapper;

    public WebAuthnController(WebAuthnService webAuthnService,
                               TokenRepository tokenRepo,
                               ObjectMapper objectMapper) {
        this.webAuthnService = webAuthnService;
        this.tokenRepo = tokenRepo;
        this.objectMapper = objectMapper;
    }

    // --- Registration ---

    @PostMapping(value = "/register/begin",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerBegin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized"));
        }

        String userId = token.get().getSubject();
        String displayName = body != null && body.containsKey("displayName")
            ? String.valueOf(body.get("displayName"))
            : userId;

        RegistrationBeginResult result = webAuthnService.beginRegistration(userId, displayName);

        return ResponseEntity.ok(Map.of(
            "challenge", result.options().challenge(),
            "userId", result.options().userId(),
            "displayName", result.options().displayName(),
            "rpName", result.options().rpName(),
            "credentialUid", result.credentialUid(),
            "timeout", 60000 // 60 seconds
        ));
    }

    @PostMapping(value = "/register/complete",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerComplete(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized"));
        }

        String userId = token.get().getSubject();
        String response = body != null && body.containsKey("response")
            ? String.valueOf(body.get("response")) : "";
        String credentialUid = body != null && body.containsKey("credentialUid")
            ? String.valueOf(body.get("credentialUid")) : "";
        String deviceType = body != null && body.containsKey("deviceType")
            ? String.valueOf(body.get("deviceType")) : "unknown";

        boolean success = webAuthnService.completeRegistration(response, credentialUid, userId, deviceType);

        if (success) {
            return ResponseEntity.ok(Map.of("registered", true));
        } else {
            return ResponseEntity.status(400)
                .body(Map.of("registered", false, "error", "attestation verification failed"));
        }
    }

    // --- Authentication ---

    @PostMapping(value = "/authenticate/begin",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> authenticateBegin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized"));
        }

        String userId = token.get().getSubject();
        AuthenticationBeginResult result = webAuthnService.beginAuthentication(userId);

        if (result == null) {
            return ResponseEntity.ok(Map.of(
                "error", "no_credentials",
                "message", "No WebAuthn credentials registered for this user"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "challenge", result.options().challenge(),
            "userId", result.options().userId(),
            "allowCredentials", result.options().allowedCredentialIds(),
            "timeout", 60000
        ));
    }

    @PostMapping(value = "/authenticate/complete",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> authenticateComplete(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized"));
        }

        String userId = token.get().getSubject();
        String response = body != null && body.containsKey("response")
            ? String.valueOf(body.get("response")) : "";

        boolean success = webAuthnService.completeAuthentication(response, userId);

        if (success) {
            return ResponseEntity.ok(Map.of("authenticated", true));
        } else {
            return ResponseEntity.status(401)
                .body(Map.of("authenticated", false, "error", "assertion verification failed"));
        }
    }

    // --- Bearer token validation ---

    private Optional<Token> validateBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String tokenValue = authHeader.substring("Bearer ".length()).trim();
        if (tokenValue.isBlank()) return Optional.empty();
        return tokenRepo.findByJtiAndRevokedFalse(tokenValue)
            .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(Instant.now()));
    }
}
