package com.iam.mfa.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import com.iam.mfa.service.TotpService;
import com.iam.mfa.service.TotpService.TotpSetupResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * TOTP MFA endpoints per RFC 6238.
 *
 * POST /mfa/totp/setup   — Enroll: generates secret + QR code for authenticated user
 * POST /mfa/totp/verify  — Verify: validates 6-digit code; marks credential verified
 */

@RestController
@RequestMapping("/mfa/totp")
public class TotpController {

    private static final String ISSUER = "IAMProtocolEngine";

    private final TotpService totpService;
    private final TokenRepository tokenRepo;
    private final ObjectMapper objectMapper;

    public TotpController(TotpService totpService,
                          TokenRepository tokenRepo,
                          ObjectMapper objectMapper) {
        this.totpService = totpService;
        this.tokenRepo = tokenRepo;
        this.objectMapper = objectMapper;
    }

    // --- Setup (enrollment) ---

    @PostMapping(value = "/setup",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setup(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized", "message", "Bearer token required"));
        }

        String subject = token.get().getSubject();
        TotpSetupResult result = totpService.generateSetup(subject, ISSUER);

        return ResponseEntity.ok(Map.of(
            "secret", result.secret(),
            "provisioningUri", result.provisioningUri(),
            "qrCodeImage", "data:image/png;base64," + result.qrCodeImage()
        ));
    }

    // --- Verify ---

    @PostMapping(value = "/verify",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verify(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized", "message", "Bearer token required"));
        }

        if (body == null || !body.containsKey("code")) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_request", "message", "code is required"));
        }

        String code = String.valueOf(body.get("code")).trim();
        String subject = token.get().getSubject();

        boolean valid = totpService.verify(subject, code);

        if (valid) {
            return ResponseEntity.ok(Map.of(
                "verified", true,
                "message", "TOTP verified successfully"
            ));
        } else {
            return ResponseEntity.status(401)
                .body(Map.of(
                    "verified", false,
                    "error", "invalid_code",
                    "message", "TOTP code is invalid or expired"
                ));
        }
    }

    // --- Internal: check enrollment status ---

    @GetMapping(value = "/status",
               produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Optional<Token> token = validateBearerToken(authHeader);
        if (token.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "unauthorized", "message", "Bearer token required"));
        }

        String subject = token.get().getSubject();
        boolean enrolled = totpService.isEnrolled(subject);

        return ResponseEntity.ok(Map.of(
            "enrolled", enrolled,
            "userId", subject
        ));
    }

    // --- Bearer token validation (same pattern as SCIM controllers) ---

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
