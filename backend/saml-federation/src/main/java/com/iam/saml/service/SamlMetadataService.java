package com.iam.saml.service;

import com.iam.oauth.security.JwksService;
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
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Generates the SP metadata XML document per SAML 2.0 §2.
 *
 * The metadata tells the IdP:
 * - Our EntityID (unique identifier for this SP)
 * - Our ACS URL (AssertionConsumerService — where IdP POSTs SAMLResponse)
 * - Supported NameID formats
 * - Our signing public key (so IdP can verify AuthnRequest signatures)
 *
 * Uses OpenSAML for XML building and pure JDK JSR 105 for XML signing.
 */
@Service
public class SamlMetadataService {

    private final JwksService jwksService;
    private final String spEntityId;
    private final String spAcsUrl;
    private final String spBaseUrl;

    public SamlMetadataService(
            JwksService jwksService,
            @Value("${saml.sp.entity-id}") String spEntityId,
            @Value("${saml.sp.acs-url}") String spAcsUrl,
            @Value("${saml.sp.base-url}") String spBaseUrl) {
        this.jwksService = jwksService;
        this.spEntityId = spEntityId;
        this.spAcsUrl = spAcsUrl;
        this.spBaseUrl = spBaseUrl;
    }

    /**
     * Generates the SP metadata XML as a formatted String.
     * The metadata XML is manually built using StringBuilder and DOM.
     * Signature is applied using JDK XML Digital Signature (JSR 105).
     */
    public String generateSpMetadata() {
        try {
            String unsignedMetadata = buildMetadataXml();
            var doc = parseXmlString(unsignedMetadata);
            signDocumentWithJsr105(doc);
            return documentToString(doc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate SAML metadata", e);
        }
    }

    /**
     * Builds the SP metadata XML string (unsigned).
     * Creates a SAML 2.0 EntityDescriptor with embedded SPSSODescriptor.
     */
    private String buildMetadataXml() throws java.security.cert.CertificateEncodingException {
        X509Certificate spCert = getSpSigningCertificate();
        String certStr = Base64.getEncoder().encodeToString(spCert.getEncoded());

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                                ID="_sp-%s"
                                entityID="%s">
              <md:SPSSODescriptor AuthnRequestsSigned="true"
                                  WantAssertionsSigned="false"
                                  protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</md:NameIDFormat>
                <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat>
                <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress</md:NameIDFormat>
                <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                                             Location="%s"
                                             index="0"
                                             isDefault="true"/>
                <md:KeyDescriptor use="signing">
                  <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                    <ds:X509Data>
                      <ds:X509Certificate>%s</ds:X509Certificate>
                    </ds:X509Data>
                  </ds:KeyInfo>
                </md:KeyDescriptor>
              </md:SPSSODescriptor>
            </md:EntityDescriptor>
            """.formatted(
                spEntityId.hashCode(),
                escapeXml(spEntityId),
                escapeXml(spAcsUrl),
                certStr.replace("\n", "")
            );
    }

    /**
     * Signs the DOM document using JDK XML Digital Signature (JSR 105).
     * Uses RSA-SHA256 for the signature algorithm and exclusive canonical XML.
     */
    private void signDocumentWithJsr105(org.w3c.dom.Document doc) throws Exception {
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
    }

    private org.w3c.dom.Document parseXmlString(String xml) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var builder = factory.newDocumentBuilder();
        return builder.parse(new java.io.ByteArrayInputStream(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private String documentToString(org.w3c.dom.Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // --- Keystore access for SamlAuthnRequestService ---

    KeyStore getKeystore() {
        return jwksService.getKeystore();
    }

    List<String> getAllKids() {
        return jwksService.getAllKids();
    }

    char[] getKeystorePassword() {
        return jwksService.getKeystorePassword();
    }

    // --- Signing certificate / key ---

    private X509Certificate getSpSigningCertificate() {
        try {
            KeyStore ks = jwksService.getKeystore();
            String kid = jwksService.getAllKids().get(jwksService.getAllKids().size() - 1);
            java.security.cert.Certificate cert = ks.getCertificate(kid);
            if (cert instanceof X509Certificate x509) {
                return x509;
            }
            throw new IllegalStateException("Certificate for kid '" + kid + "' is not X509Certificate");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SP signing certificate", e);
        }
    }

    PrivateKey getSpSigningPrivateKey() {
        try {
            KeyStore ks = jwksService.getKeystore();
            String kid = jwksService.getAllKids().get(jwksService.getAllKids().size() - 1);
            java.security.Key key = ks.getKey(kid, jwksService.getKeystorePassword());
            if (key instanceof PrivateKey pk) {
                return pk;
            }
            throw new IllegalStateException("Key for kid '" + kid + "' is not PrivateKey");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SP signing private key", e);
        }
    }
}
