package com.iam.oauth.controller;

import com.iam.authcore.entity.DeviceCode;
import com.iam.authcore.repository.DeviceCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.oauth.dto.TokenRequest;
import com.iam.oauth.dto.TokenResponse;
import com.iam.oauth.service.TokenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 Token Endpoint per RFC 6749 §3.2.
 *
 * POST /oauth2/token
 *
 * Handles:
 * - grant_type=authorization_code  (code exchange with PKCE)
 * - grant_type=client_credentials (machine-to-machine)
 * - grant_type=refresh_token      (token refresh/rotation)
 * - grant_type=urn:ietf:params:oauth:grant-type:device_code (RFC 8628)
 */
@RestController
@RequestMapping("/oauth2")
public class TokenController {

    private final TokenService tokenService;
    private final DeviceCodeRepository deviceCodeRepo;
    private final OAuthClientRepository clientRepo;

    public TokenController(TokenService tokenService,
                            DeviceCodeRepository deviceCodeRepo,
                            OAuthClientRepository clientRepo) {
        this.tokenService = tokenService;
        this.deviceCodeRepo = deviceCodeRepo;
        this.clientRepo = clientRepo;
    }

    /**
     * Token endpoint per RFC 6749 §3.2.
     *
     * For authorization_code grant, client_id may be in the body or
     * via HTTP Basic auth header (RFC 6749 §2.3.1). Here we support both.
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> token(
            @RequestParam(value = "grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "device_code", required = false) String deviceCode,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        // Support client_id from Basic auth header (RFC 6749 §2.3.1)
        if (clientId == null && authHeader != null && authHeader.startsWith("Basic ")) {
            String[] credentials = decodeBasicAuth(authHeader);
            if (credentials != null) {
                clientId = credentials[0];
                clientSecret = credentials[1];
            }
        }

        // RFC 8628: Device Authorization Grant
        if ("urn:ietf:params:oauth:grant-type:device_code".equals(grantType)) {
            return handleDeviceCodeGrant(clientId, deviceCode);
        }

        TokenRequest request = new TokenRequest(
            grantType, code, redirectUri, codeVerifier,
            clientId, clientSecret, refreshToken, scope
        );

        TokenResponse response = tokenService.handleTokenRequest(request);

        if (response.error() != null) {
            // RFC 6749 §5.2: token errors return 400
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * RFC 8628 §3.5 — Device Code Polling.
     *
     * Device polls this endpoint with device_code until:
     * - User approves → tokens issued (200)
     * - User denies → authorization_denied (400)
     * - Expired → token expired (400)
     * - Not yet approved → authorization_pending (400)
     */
    private ResponseEntity<TokenResponse> handleDeviceCodeGrant(String clientId, String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return ResponseEntity.badRequest()
                .body(TokenResponse.error("invalid_request", "device_code required"));
        }
        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest()
                .body(TokenResponse.error("invalid_request", "client_id required"));
        }

        Optional<DeviceCode> opt = deviceCodeRepo.findById(deviceCode);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(TokenResponse.error("invalid_grant", "device_code not found"));
        }

        DeviceCode dc = opt.get();

        // Verify client_id matches
        if (!dc.getClientId().equals(clientId)) {
            return ResponseEntity.badRequest()
                .body(TokenResponse.error("invalid_grant", "client_id mismatch"));
        }

        // Check expiry
        if (dc.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.badRequest()
                .body(TokenResponse.error("token_expired", "device code has expired"));
        }

        switch (dc.getStatus()) {
            case approved -> {
                // Issue tokens
                TokenResponse response = tokenService.issueTokensForSamlUser(
                    dc.getUserCode(),  // subject = user_code identifier
                    dc.getClientId(),
                    null,   // no nonce in device flow
                    dc.getScope()
                );
                // Consume device code
                deviceCodeRepo.deleteById(deviceCode);
                return ResponseEntity.ok(response);
            }
            case denied -> {
                return ResponseEntity.badRequest()
                    .body(TokenResponse.error("access_denied", "user denied the request"));
            }
            case expired -> {
                return ResponseEntity.badRequest()
                    .body(TokenResponse.error("token_expired", "device code expired"));
            }
            default -> {
                // pending — increment polling count
                dc.setPollingCount(dc.getPollingCount() + 1);
                deviceCodeRepo.save(dc);
                return ResponseEntity.badRequest()
                    .body(TokenResponse.error("authorization_pending", "waiting for user approval"));
            }
        }
    }

    private String[] decodeBasicAuth(String header) {
        try {
            String encoded = header.substring("Basic ".length());
            byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
            String pair = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            int colon = pair.indexOf(':');
            if (colon < 0) return null;
            return new String[]{pair.substring(0, colon), pair.substring(colon + 1)};
        } catch (Exception e) {
            return null;
        }
    }
}
