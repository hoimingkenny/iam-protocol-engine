package com.iam.saml.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.oauth.dto.TokenResponse;
import com.iam.oauth.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SAML → OIDC Claim Bridge.
 *
 * After a SAML assertion is validated, this service:
 * 1. Decodes the RelayState to extract client_id and redirect_uri
 * 2. Calls TokenService to issue access token, refresh token, and ID token
 * 3. Builds the final redirect URL with tokens
 *
 * Tokens can be delivered via:
 * - Query parameters (default for SAML): ?access_token=...&id_token=...&redirect_uri=...
 * - Fragment (#): used by OIDC for implicit/hybrid flows
 *
 * For SP-initiated SSO with SAML, query params are preferred per SAML 2.0 §3.4.4.
 */
@Service
public class SamlToOidcTokenService {

    private static final Logger log = LoggerFactory.getLogger(SamlToOidcTokenService.class);

    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SamlToOidcTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Bridge result containing issued tokens and redirect URL.
     */
    public record BridgeResult(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            String idToken,
            String scope
    ) {}

    /**
     * Issues OIDC tokens after SAML assertion validation and builds the redirect URL.
     *
     * @param nameId the SAML NameID (OAuth subject / sub claim)
     * @param sessionIndex the SAML session index (not used in tokens, stored for logout)
     * @param relayState the RelayState from the AuthnRequest (Base64URL-encoded JSON)
     * @param nonce optional nonce from the AuthnRequest (may be null)
     * @return BridgeResult with tokens, or null if client not found
     */
    public BridgeResult bridge(String nameId, String sessionIndex,
                                String relayState, String nonce) {
        // Decode RelayState: {"client_id":"...","redirect_uri":"..."}
        String clientId;
        String redirectUri;
        try {
            String decoded = new String(
                Base64.getUrlDecoder().decode(relayState), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(decoded);
            clientId = node.get("client_id").asText();
            redirectUri = node.get("redirect_uri").asText();
        } catch (Exception e) {
            log.error("Failed to decode RelayState: {}", relayState, e);
            throw new IllegalArgumentException("Invalid RelayState");
        }

        log.info("Issuing tokens for SAML user '{}' to client '{}', redirecting to {}",
            nameId, clientId, redirectUri);

        // Issue tokens
        TokenResponse tokenResponse = tokenService.issueTokensForSamlUser(
            nameId, clientId, nonce, null);

        if (tokenResponse.error() != null) {
            log.error("Token issuance failed: {}", tokenResponse.error());
            throw new IllegalStateException("Token issuance failed: " + tokenResponse.error());
        }

        return new BridgeResult(
            tokenResponse.accessToken(),
            tokenResponse.tokenType(),
            tokenResponse.expiresIn(),
            tokenResponse.refreshToken(),
            tokenResponse.idToken(),
            tokenResponse.scope()
        );
    }

    /**
     * Builds the redirect URL with tokens as query parameters.
     * Per SAML 2.0 §3.4.4, query params are used for redirect binding.
     *
     * Format: redirect_uri?access_token=...&id_token=...&state=...
     */
    public String buildRedirectUrl(String redirectUri, BridgeResult result, String state) {
        try {
            String query = "access_token=" + java.net.URLEncoder.encode(result.accessToken(), StandardCharsets.UTF_8)
                + "&token_type=" + java.net.URLEncoder.encode(result.tokenType(), StandardCharsets.UTF_8)
                + "&expires_in=" + result.expiresIn()
                + (result.idToken() != null
                    ? "&id_token=" + java.net.URLEncoder.encode(result.idToken(), StandardCharsets.UTF_8)
                    : "")
                + (result.refreshToken() != null
                    ? "&refresh_token=" + java.net.URLEncoder.encode(result.refreshToken(), StandardCharsets.UTF_8)
                    : "")
                + (state != null
                    ? "&state=" + java.net.URLEncoder.encode(state, StandardCharsets.UTF_8)
                    : "");

            String separator = redirectUri.contains("?") ? "&" : "?";
            return redirectUri + separator + query;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build redirect URL", e);
        }
    }
}
