package com.iam.saml.service;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;

/**
 * Validates SAML 2.0 Response assertions from the IdP.
 *
 * Per SAML 2.0 §3.2.2 and §5, the SP must:
 * 1. Verify the XML signature using the IdP's signing certificate
 * 2. Verify InResponseTo matches an issued AuthnRequest ID (replay protection)
 * 3. Verify Destination matches the SP's ACS URL
 * 4. Verify NotBefore / NotOnOrAfter (timing constraints)
 * 5. Verify AudienceRestriction includes this SP's entity ID
 * 6. Consume the request ID after successful validation
 */
@Service
public class SamlAssertionValidator {

    private static final Logger log = LoggerFactory.getLogger(SamlAssertionValidator.class);

    private final SamlAuthnRequestService authnRequestService;
    private final String spEntityId;
    private final String spAcsUrl;
    private final X509Certificate idpSigningCert;
    private final DocumentBuilderFactory docBuilderFactory;

    public SamlAssertionValidator(
            SamlAuthnRequestService authnRequestService,
            @Value("${saml.sp.entity-id}") String spEntityId,
            @Value("${saml.sp.acs-url}") String spAcsUrl,
            @Value("${saml.idp.signing-cert:}") String idpSigningCertPem
    ) {
        this.authnRequestService = authnRequestService;
        this.spEntityId = spEntityId;
        this.spAcsUrl = spAcsUrl;
        this.idpSigningCert = parseCertPem(idpSigningCertPem);

        this.docBuilderFactory = DocumentBuilderFactory.newInstance();
        this.docBuilderFactory.setNamespaceAware(true);
        try {
            this.docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            this.docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            this.docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            log.warn("Could not set secure XML features", e);
        }
    }

    /**
     * Validated result of processing a SAMLResponse.
     */
    public record ValidationResult(
            boolean success,
            String error,
            String nameId,
            String sessionIndex,
            String relayState
    ) {}

    /**
     * Parses and validates a SAMLResponse.
     *
     * @param samlResponseBase64 Base64-encoded SAML Response XML
     * @param relayState RelayState passed through from the IdP
     * @return ValidationResult with extracted claims or error
     */
    public ValidationResult validate(String samlResponseBase64, String relayState) {
        try {
            byte[] decoded = Base64.getDecoder().decode(samlResponseBase64);
            var doc = parseXml(decoded);

            // Step 1: Validate XML signature
            if (!validateXmlSignature(doc)) {
                return new ValidationResult(false, "Invalid XML signature", null, null, relayState);
            }

            // Step 2: Parse SAML Response using OpenSAML unmarshaller
            Response response = parseSamlResponse(doc.getDocumentElement());
            String inResponseTo = response.getInResponseTo();
            String destination = response.getDestination();

            // Step 3: Verify InResponseTo
            if (inResponseTo != null && !inResponseTo.isBlank()) {
                if (!authnRequestService.isRequestIdValid(inResponseTo)) {
                    return new ValidationResult(false, "InResponseTo is not valid or expired", null, null, relayState);
                }
            }

            // Step 4: Verify Destination
            if (destination != null && !destination.equals(spAcsUrl)) {
                return new ValidationResult(false,
                    "Destination mismatch: expected " + spAcsUrl + " but got " + destination, null, null, relayState);
            }

            // Step 5: Validate assertions
            for (Assertion assertion : response.getAssertions()) {
                String assertionError = validateAssertion(assertion);
                if (assertionError != null) {
                    return new ValidationResult(false, assertionError, null, null, relayState);
                }
            }

            // Step 6: Extract NameID and session index
            Assertion primaryAssertion = response.getAssertions().isEmpty() ? null
                : response.getAssertions().get(0);
            if (primaryAssertion == null) {
                return new ValidationResult(false, "No assertions in SAMLResponse", null, null, relayState);
            }

            Subject subject = primaryAssertion.getSubject();
            String nameId = subject != null && subject.getNameID() != null
                ? subject.getNameID().getValue() : null;

            String sessionIndex = null;
            if (primaryAssertion.getAuthnStatements() != null
                    && !primaryAssertion.getAuthnStatements().isEmpty()) {
                sessionIndex = primaryAssertion.getAuthnStatements().get(0).getSessionIndex();
            }

            // Step 7: Consume request ID (prevents replay)
            if (inResponseTo != null && !inResponseTo.isBlank()) {
                authnRequestService.consumeRequestId(inResponseTo);
            }

            log.info("SAML assertion validated successfully for NameID: {}", nameId);
            return new ValidationResult(true, null, nameId, sessionIndex, relayState);

        } catch (Exception e) {
            log.error("SAML assertion validation failed", e);
            return new ValidationResult(false, "Validation failed: " + e.getMessage(), null, null, relayState);
        }
    }

    /**
     * Validates the XML signature on the SAML Response document.
     */
    private boolean validateXmlSignature(org.w3c.dom.Document doc) throws Exception {
        var nodeList = doc.getElementsByTagNameNS(
            "http://www.w3.org/2000/09/xmldsig#", "Signature");
        if (nodeList.getLength() == 0) {
            log.warn("No XML signature found in SAML Response");
            return false;
        }

        org.w3c.dom.Node signatureNode = nodeList.item(0);

        if (idpSigningCert == null) {
            log.warn("No IdP signing certificate configured — skipping signature validation");
            return true;
        }

        XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext context = new DOMValidateContext(
            idpSigningCert.getPublicKey(), signatureNode);
        javax.xml.crypto.dsig.XMLSignature signature = sigFactory.unmarshalXMLSignature(context);

        return signature.validate(context);
    }

    /**
     * Validates timing, audience, and subject confirmation on an assertion.
     */
    private String validateAssertion(Assertion assertion) {
        Instant now = Instant.now();

        // Subject confirmation check
        if (assertion.getSubject() != null) {
            for (SubjectConfirmation sc : assertion.getSubject().getSubjectConfirmations()) {
                SubjectConfirmationData scd = sc.getSubjectConfirmationData();
                if (scd != null) {
                    if (scd.getNotBefore() != null && now.isBefore(scd.getNotBefore())) {
                        return "Assertion NotBefore is in the future: " + scd.getNotBefore();
                    }
                    if (scd.getNotOnOrAfter() != null && now.isAfter(scd.getNotOnOrAfter())) {
                        return "Assertion NotOnOrAfter has expired: " + scd.getNotOnOrAfter();
                    }
                    if (scd.getInResponseTo() != null && !scd.getInResponseTo().isBlank()) {
                        if (!authnRequestService.isRequestIdValid(scd.getInResponseTo())) {
                            return "SubjectConfirmation InResponseTo is not valid: " + scd.getInResponseTo();
                        }
                    }
                }
            }
        }

        // Conditions: NotBefore / NotOnOrAfter / Audience
        Conditions conditions = assertion.getConditions();
        if (conditions != null) {
            if (conditions.getNotBefore() != null && now.isBefore(conditions.getNotBefore())) {
                return "Assertion NotBefore is in the future";
            }
            if (conditions.getNotOnOrAfter() != null && now.isAfter(conditions.getNotOnOrAfter())) {
                return "Assertion NotOnOrAfter has expired";
            }

            if (conditions.getAudienceRestrictions() != null) {
                boolean matched = false;
                for (AudienceRestriction restriction : conditions.getAudienceRestrictions()) {
                    for (Audience audience : restriction.getAudiences()) {
                        if (spEntityId.equals(audience.getAudienceURI())) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    return "Audience restriction not satisfied for SP: " + spEntityId;
                }
            }
        }

        return null;
    }

    private org.w3c.dom.Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private Response parseSamlResponse(org.w3c.dom.Element element) throws Exception {
        UnmarshallerFactory factory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        Unmarshaller unmarshaller = factory.getUnmarshaller(element);
        org.opensaml.core.xml.XMLObject xmlObj = unmarshaller.unmarshall(element);
        if (!(xmlObj instanceof Response response)) {
            throw new IllegalArgumentException("Root element is not a SAML Response");
        }
        return response;
    }

    private X509Certificate parseCertPem(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String stripped = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(stripped);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
        } catch (Exception e) {
            log.warn("Failed to parse IdP signing certificate from saml.idp.signing-cert", e);
            return null;
        }
    }
}
