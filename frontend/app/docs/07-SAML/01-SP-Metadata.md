---
title: SP Metadata
description: How the SP generates and signs its metadata XML document (SAML 2.0).
---

# SP Metadata — `/saml/metadata`

## What Is SP Metadata?

SAML SP metadata is an XML document that tells the IdP who you are and where to send assertions. It contains the SP's entityID, the ACS URL, and the SP's public signing key. The IdP administrator registers this metadata (or manually configures these values) before SSO can work.

## The Metadata XML

```
GET /saml/metadata
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<md:EntityDescriptor
    xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
    entityID="http://localhost:8080/saml/sp">

    <md:SPSSODescriptor
        protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">

        <md:KeyDescriptor use="signing">
            <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                <ds:X509Data>
                    <ds:X509Certificate>
                        MIIC...  <!-- SP public key for IdP to verify signatures -->
                    </ds:X509Certificate>
                </ds:X509Data>
            </ds:KeyInfo>
        </md:KeyDescriptor>

        <md:AssertionConsumerService
            Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
            Location="http://localhost:8080/saml/acs"
            index="0"
            isDefault="true"/>

    </md:SPSSODescriptor>
</md:EntityDescriptor>
```

The metadata is **signed** with the SP's private key. The IdP verifies this signature to confirm the metadata hasn't been tampered with.

## How It's Generated

```java
// SamlMetadataService.java
public String generateSpMetadata() {
    // 1. Build EntityDescriptor with entityID
    // 2. Add SPSSODescriptor with protocol support enumeration
    // 3. Embed the SP signing public key (from JwksService keystore)
    // 4. Add AssertionConsumerService with HTTP-POST binding
    // 5. Sign the entire document with RSA-SHA256 (JSR 105)
    // 6. Return as UTF-8 string
}
```

## Key Design Decisions

### Why Sign Metadata?

The IdP will use the embedded public key to encrypt assertions or verify signed AuthnRequests. If an attacker could modify the metadata in transit, they could substitute their own public key and impersonate the SP. XML signature prevents this.

### Why RSA-SHA256?

SHA-256 with RSA is the de facto standard for SAML 2.0. Every IdP supports it. The signature uses the SP's private key from the same keystore used for OIDC JWT signing — a deliberate choice to share the key infrastructure.

### Inclusive vs Exclusive Canonicalization

We use **inclusive canonical XML with comments** (`INCLUSIVE_WITH_COMMENTS`). Exclusive canonical XML (`exc-c14n#with-comments`) is theoretically more correct for signed XML but is not available in the JDK's XML Digital Signature implementation. Inclusive c14n is widely interoperable and works with all major IdPs.

## Properties Required

```properties
# application.properties (api-gateway)
saml.sp.entity-id=http://localhost:8080/saml/sp
saml.sp.acs-url=http://localhost:8080/saml/acs
saml.sp.base-url=http://localhost:8080
```

These are used to fill in the `entityID`, `AssertionConsumerService Location`, and other elements in the metadata.
