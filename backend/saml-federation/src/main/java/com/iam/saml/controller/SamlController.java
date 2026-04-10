package com.iam.saml.controller;

import com.iam.saml.service.SamlAuthnRequestService;
import com.iam.saml.service.SamlMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * SAML 2.0 SP endpoints per SAML 2.0 spec §3.
 *
 * Task 20: SP Metadata + AuthnRequest
 * Task 21: ACS endpoint (separate controller)
 */
@RestController
@RequestMapping("/saml")
public class SamlController {

    private final SamlMetadataService metadataService;
    private final SamlAuthnRequestService authnRequestService;

    public SamlController(SamlMetadataService metadataService,
                          SamlAuthnRequestService authnRequestService) {
        this.metadataService = metadataService;
        this.authnRequestService = authnRequestService;
    }

    /**
     * GET /saml/metadata — SP metadata XML.
     *
     * This endpoint returns the SP's metadata document (SAML 2.0 §2.3).
     * The IdP uses this to learn:
     * - Our EntityID
     * - Our AssertionConsumerService URL
     * - What NameID formats we accept
     * - Our signing public key (for verifying AuthnRequest signatures)
     *
     * In a real deployment, this metadata is registered with or exchanged
     * with the IdP out-of-band.
     *
     * @return XML metadata document, Content-Type: application/samlmetadata+xml
     */
    @GetMapping(value = "/metadata",
               produces = {
                   "application/samlmetadata+xml",
                   "application/xml",
                   MediaType.APPLICATION_XML_VALUE
               })
    public ResponseEntity<String> getMetadata() {
        String metadata;
        try {
            metadata = metadataService.generateSpMetadata();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("<!-- Error generating metadata: " + e.getMessage() + " -->");
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .body(metadata);
    }

    /**
     * GET /saml/initiate — Start SP-initiated SSO.
     *
     * Builds a signed AuthnRequest and redirects the browser to the IdP's SSO URL.
     * The IdP will authenticate the user and POST a SAMLResponse to our ACS URL.
     *
     * @param clientId the OAuth client ID (used in RelayState)
     * @param redirectUri the URL to redirect to after successful SSO (used in RelayState)
     * @return 302 redirect to IdP with SAMLRequest and RelayState query params
     */
    @GetMapping(value = "/initiate")
    public ResponseEntity<Void> initiateSso(
            @RequestParam(value = "client_id") String clientId,
            @RequestParam(value = "redirect_uri") String redirectUri
    ) {
        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String redirectUrl = authnRequestService.buildAuthnRequestRedirect(clientId, redirectUri);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build();
    }
}
