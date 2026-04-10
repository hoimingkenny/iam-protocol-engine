package com.iam.saml.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds, signs, and encodes SAML 2.0 AuthnRequests.
 *
 * The AuthnRequest is the first message in SP-initiated SSO (SAML 2.0 §3.4).
 * It is signed with the SP's private key so the IdP can verify its authenticity.
 *
 * Request IDs are stored in-memory with expiry for replay protection
 * (SAML 2.0 §3.4.1: IdP discards requests with IDs already processed).
 *
 * Uses pure JDK JSR 105 for XML signing — avoids OpenSAML signing API complexity.
 */
@Service
public class SamlAuthnRequestService {

    private static final Duration AUTHN_REQUEST_TTL = Duration.ofMinutes(5);

    private final SamlMetadataService metadataService;
    private final String idpSsoUrl;
    private final String spEntityId;
    private final String spAcsUrl;

    /** In-memory store of issued request IDs with expiry time. */
    private final Map<String, Instant> issuedRequestIds = new ConcurrentHashMap<>();

    public SamlAuthnRequestService(
            SamlMetadataService metadataService,
            @Value("${saml.idp.sso-url}") String idpSsoUrl,
            @Value("${saml.sp.entity-id}") String spEntityId,
            @Value("${saml.sp.acs-url}") String spAcsUrl) {
        this.metadataService = metadataService;
        this.idpSsoUrl = idpSsoUrl;
        this.spEntityId = spEntityId;
        this.spAcsUrl = spAcsUrl;
    }

    /**
     * Builds and signs a SAML 2.0 AuthnRequest.
     * Returns a redirect URL to the IdP with the encoded, deflated, signed AuthnRequest.
     *
     * @param clientId the OAuth client ID (used to build the RelayState)
     * @param redirectUri the redirect URI after successful SSO
     * @return redirect URL to IdP's SSO endpoint with SAMLRequest and RelayState query params
     */
    public String buildAuthnRequestRedirect(String clientId, String redirectUri) {
        try {
            String requestId = "_" + UUID.randomUUID().toString().replace("-", "");
            String unsignedXml = buildUnsignedAuthnRequestXml(requestId);
            String signedXml = signAuthnRequestXml(unsignedXml);

            // Store request ID for replay protection
            issuedRequestIds.put(requestId, Instant.now().plus(AUTHN_REQUEST_TTL));

            // Encode for redirect binding: XML → UTF-8 → Deflate → Base64URL
            String encoded = encodeForRedirectBinding(signedXml);

            // Build redirect URL
            String relayState = buildRelayState(clientId, redirectUri);
            return idpSsoUrl
                + "?SAMLRequest=" + encoded
                + "&RelayState=" + java.net.URLEncoder.encode(relayState, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to build AuthnRequest", e);
        }
    }

    /**
     * Validates that a request ID was previously issued (replay protection).
     */
    public boolean isRequestIdValid(String requestId) {
        Instant expiry = issuedRequestIds.get(requestId);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            issuedRequestIds.remove(requestId);
            return false;
        }
        return true;
    }

    /**
     * Consumes a request ID — removes it from the issued set.
     */
    public void consumeRequestId(String requestId) {
        issuedRequestIds.remove(requestId);
    }

    /**
     * Cleans up expired request IDs from memory.
     */
    public void cleanupExpiredRequestIds() {
        Instant now = Instant.now();
        issuedRequestIds.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }

    /**
     * Builds the unsigned AuthnRequest XML string.
     */
    private String buildUnsignedAuthnRequestXml(String requestId) {
        Instant now = Instant.now();
        String issueInstant = now.toString();

        return """
            <samlp:AuthnRequest
                xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="%s"
                IssueInstant="%s"
                Destination="%s"
                AssertionConsumerServiceURL="%s"
                ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                IsPassive="false"
                ForceAuthn="false">
              <saml:Issuer>%s</saml:Issuer>
              <samlp:NameIDPolicy
                  Format="urn:oasis:names:tc:SAML:2.0:nameid-format:unspecified"
                  AllowCreate="true"/>
              <samlp:RequestedAuthnContext>
                <saml:AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef>
              </samlp:RequestedAuthnContext>
            </samlp:AuthnRequest>
            """.formatted(
                requestId,
                issueInstant,
                escapeXml(idpSsoUrl),
                escapeXml(spAcsUrl),
                escapeXml(spEntityId)
            );
    }

    /**
     * Signs the AuthnRequest XML using JDK JSR 105 (RSA-SHA256).
     */
    private String signAuthnRequestXml(String unsignedXml) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new java.io.ByteArrayInputStream(
            unsignedXml.getBytes(StandardCharsets.UTF_8)));

        XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");

        Reference ref = sigFactory.newReference(
            "",
            sigFactory.newDigestMethod(DigestMethod.SHA256, null),
            List.of(),
            null,
            null
        );

        SignedInfo signedInfo = sigFactory.newSignedInfo(
            sigFactory.newCanonicalizationMethod(
                javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
                (javax.xml.crypto.dsig.CanonicalizationMethod) null
            ),
            sigFactory.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
            List.of(ref)
        );

        KeyInfoFactory keyInfoFactory = sigFactory.getKeyInfoFactory();
        List<Object> x509DataContent = new ArrayList<>();
        x509DataContent.add(getSpSigningCertificate());
        X509Data x509Data = keyInfoFactory.newX509Data(x509DataContent);
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

        javax.xml.crypto.dsig.XMLSignature signature =
            sigFactory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(new javax.xml.crypto.dsig.dom.DOMSignContext(
            getSpSigningPrivateKey(), doc.getDocumentElement()));

        // Serialize the signed document
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Encodes the AuthnRequest for SAML redirect binding (SAML 2.0 §3.4.4).
     * Process: XML → UTF-8 → Deflate (compress) → Base64 → URL-safe Base64
     */
    private String encodeForRedirectBinding(String xml) throws Exception {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFLATED, true);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(deflated, deflater)) {
            dos.write(xmlBytes);
            dos.finish();
        }
        byte[] deflatedBytes = deflated.toByteArray();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(deflatedBytes);
    }

    /**
     * Builds a RelayState value from client_id and redirect_uri.
     * URL-safe Base64 encoding of JSON: {"client_id":"...","redirect_uri":"..."}
     */
    private String buildRelayState(String clientId, String redirectUri) {
        String json = "{\"client_id\":\"" + clientId + "\",\"redirect_uri\":\"" + redirectUri + "\"}";
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private X509Certificate getSpSigningCertificate() {
        try {
            KeyStore ks = metadataService.getKeystore();
            String kid = metadataService.getAllKids().get(metadataService.getAllKids().size() - 1);
            java.security.cert.Certificate cert = ks.getCertificate(kid);
            if (cert instanceof X509Certificate x509) {
                return x509;
            }
            throw new IllegalStateException("Certificate for kid '" + kid + "' is not X509Certificate");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SP signing certificate", e);
        }
    }

    private PrivateKey getSpSigningPrivateKey() {
        try {
            KeyStore ks = metadataService.getKeystore();
            String kid = metadataService.getAllKids().get(metadataService.getAllKids().size() - 1);
            java.security.Key key = ks.getKey(kid, metadataService.getKeystorePassword());
            if (key instanceof PrivateKey pk) {
                return pk;
            }
            throw new IllegalStateException("Key for kid '" + kid + "' is not PrivateKey");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SP signing private key", e);
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
