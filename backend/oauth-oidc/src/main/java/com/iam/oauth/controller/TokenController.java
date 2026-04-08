package com.iam.oauth.controller;

import com.iam.oauth.dto.TokenRequest;
import com.iam.oauth.dto.TokenResponse;
import com.iam.oauth.service.TokenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth 2.0 Token Endpoint per RFC 6749 §3.2.
 *
 * POST /oauth2/token
 *
 * Handles:
 * - grant_type=authorization_code  (code exchange with PKCE)
 * - grant_type=client_credentials (machine-to-machine)
 * - grant_type=refresh_token      (token refresh/rotation)
 */
@RestController
@RequestMapping("/oauth2")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
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
