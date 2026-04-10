package com.iam.saml.controller;

import com.iam.saml.service.SamlAssertionValidator;
import com.iam.saml.service.SamlToOidcTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * SAML 2.0 Assertion Consumer Service (ACS) endpoint.
 *
 * Receives SAMLResponse from the IdP after user authentication (HTTP-POST binding).
 * Task 21: Validates the assertion and extracts claims.
 * Task 22: Issues OIDC tokens and redirects to the relay state URL.
 */
@RestController
@RequestMapping("/saml")
public class SamlAcsController {

    private static final Logger log = LoggerFactory.getLogger(SamlAcsController.class);

    private final SamlAssertionValidator assertionValidator;
    private final SamlToOidcTokenService tokenBridge;

    public SamlAcsController(SamlAssertionValidator assertionValidator,
                             SamlToOidcTokenService tokenBridge) {
        this.assertionValidator = assertionValidator;
        this.tokenBridge = tokenBridge;
    }

    /**
     * POST /saml/acs — Assertion Consumer Service.
     *
     * Receives the SAMLResponse via HTTP-POST binding (SAML 2.0 §3.2).
     * The IdP POSTs directly to this endpoint with:
     * - SAMLResponse: Base64-encoded SAML Response XML
     * - RelayState: Opaque value to pass through to the redirect URL
     *
     * After validation:
     * 1. Extract NameID and session index from the assertion
     * 2. Decode RelayState to get client_id and redirect_uri
     * 3. Issue access token, refresh token, ID token (OIDC)
     * 4. Redirect to redirect_uri with tokens as query params
     *
     * @param samlResponse Base64-encoded SAML Response
     * @param relayState Opaque RelayState value from /saml/initiate
     * @return 302 redirect to redirect_uri with tokens, or 400 on validation failure
     */
    @RequestMapping(value = "/acs",
                   consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> assertionConsumerService(
            @RequestParam(value = "SAMLResponse") String samlResponse,
            @RequestParam(value = "RelayState", required = false) String relayState
    ) {
        if (samlResponse == null || samlResponse.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Received SAMLResponse, validating...");

        SamlAssertionValidator.ValidationResult result =
            assertionValidator.validate(samlResponse, relayState);

        if (!result.success()) {
            log.warn("SAML assertion validation failed: {}", result.error());
            return ResponseEntity.badRequest().build();
        }

        log.info("SAML assertion validated for NameID: {}, sessionIndex: {}",
            result.nameId(), result.sessionIndex());

        // Bridge: issue OIDC tokens and build redirect URL
        try {
            SamlToOidcTokenService.BridgeResult tokens =
                tokenBridge.bridge(result.nameId(), result.sessionIndex(),
                                   result.relayState(), null);

            String redirectUrl = tokenBridge.buildRedirectUrl(
                extractRedirectUri(result.relayState()), tokens, null);

            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();

        } catch (Exception e) {
            log.error("Token bridging failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    private String extractRedirectUri(String relayState) {
        if (relayState == null || relayState.isBlank()) {
            return "/"; // Default fallback
        }
        try {
            String decoded = new String(
                java.util.Base64.getUrlDecoder().decode(relayState),
                java.nio.charset.StandardCharsets.UTF_8);
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(decoded);
            return node.get("redirect_uri").asText("/");
        } catch (Exception e) {
            log.warn("Could not extract redirect_uri from RelayState", e);
            return "/";
        }
    }
}
