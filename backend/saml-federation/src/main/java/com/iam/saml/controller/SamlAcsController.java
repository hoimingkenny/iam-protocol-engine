package com.iam.saml.controller;

import com.iam.saml.service.SamlAssertionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public SamlAcsController(SamlAssertionValidator assertionValidator) {
        this.assertionValidator = assertionValidator;
    }

    /**
     * POST /saml/acs — Assertion Consumer Service.
     *
     * Receives the SAMLResponse via HTTP-POST binding (SAML 2.0 §3.2).
     * The IdP POSTs directly to this endpoint with:
     * - SAMLResponse: Base64-encoded SAML Response XML
     * - RelayState: Opaque value to pass through to the redirect URL
     *
     * Validation steps:
     * 1. Parse and validate XML signature against IdP certificate
     * 2. Verify InResponseTo matches issued AuthnRequest ID
     * 3. Verify Destination matches our ACS URL
     * 4. Verify NotBefore / NotOnOrAfter timing constraints
     * 5. Verify audience restriction
     *
     * Task 22 (SamlToOidcTokenService) will be called here to issue tokens
     * and redirect to the final redirect_uri.
     *
     * @param samlResponse Base64-encoded SAML Response
     * @param relayState Opaque RelayState value from /saml/initiate
     * @return 200 with JSON body on success (stub), 400 on validation failure
     */
    @RequestMapping(value = "/acs",
                   consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                   produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assertionConsumerService(
            @RequestParam(value = "SAMLResponse") String samlResponse,
            @RequestParam(value = "RelayState", required = false) String relayState
    ) {
        if (samlResponse == null || samlResponse.isBlank()) {
            return ResponseEntity.badRequest()
                .body("{\"error\":\"missing SAMLResponse\"}");
        }

        log.info("Received SAMLResponse, validating...");

        SamlAssertionValidator.ValidationResult result =
            assertionValidator.validate(samlResponse, relayState);

        if (!result.success()) {
            log.warn("SAML assertion validation failed: {}", result.error());
            return ResponseEntity.badRequest()
                .body("{\"error\":\"SAML validation failed: " + escapeJson(result.error()) + "\"}");
        }

        log.info("SAML assertion validated for NameID: {}, sessionIndex: {}",
            result.nameId(), result.sessionIndex());

        // Task 22 will: issue tokens, redirect to redirect_uri
        // For now, return a JSON response with the extracted data
        return ResponseEntity.ok()
            .body("""
                {
                  "name_id": "%s",
                  "session_index": %s,
                  "relay_state": "%s",
                  "message": "Assertion validated. Token issuance (Task 22) pending."
                }
                """.formatted(
                    escapeJson(result.nameId()),
                    result.sessionIndex() != null ? "\"" + escapeJson(result.sessionIndex()) + "\"" : "null",
                    result.relayState() != null ? escapeJson(result.relayState()) : ""
                ));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
