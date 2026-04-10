---
title: WebAuthn / FIDO2
sidebar_position: 3
description: W3C Web Authentication (WebAuthn) / FIDO2 — passwordless authentication using public key cryptography.
---

# WebAuthn / FIDO2

**W3C Spec:** [Web Authentication: An API for Accessing Public Key Credentials](https://www.w3.org/TR/webauthn-3/)
**Library:** [`com.webauthn4j:webauthn4j-core`](https://github.com/webauthn4j/webauthn4j) v0.31.2.RELEASE
**Key files:** `backend/mfa/src/main/java/com/iam/mfa/service/WebAuthnService.java`

---

## How WebAuthn Works

WebAuthn replaces passwords with public key cryptography. Instead of a shared secret:

1. **Registration:** The device generates a key pair. Private key stays on device; public key is stored on the server.
2. **Authentication:** The device proves possession of the private key by signing a server-provided challenge.

```
Registration:
  Device generates keypair (private key never leaves device)
  Server stores: credentialId, publicKey, aaguid, signCount

Authentication:
  Server sends challenge (random bytes)
  Device signs challenge with private key
  Server verifies signature with stored public key + checks signCount
```

---

## Registration Ceremony (WebAuthn §5.1)

```
Browser/App                         Server                      MFA Module
    |                                |                               |
    |-- begin registration ------ --->|                               |
    |   (userId, rp name)             |-- beginRegistration() ----- >|
    |                                 |   generate challenge (SecureRandom)
    |<-- { challenge, rpId, pubKeyOps }|                               |
    |                                 |                               |
    [User touches security key / biometric]                          |
    |   (private key signs challenge)                                |
    |-- complete registration -------->|                               |
    |   (credentialId, attestation)   |-- completeRegistration() -- >|
    |                                 |   validate attestation
    |                                 |   store credential with signCount=0
    |<-- { verified: true } ----------|                               |
```

---

## Authentication Ceremony (WebAuthn §5.2)

```
Browser/App                         Server                      MFA Module
    |                                |                               |
    |-- begin authentication ------ ->|                               |
    |   (userId, credentialId)        |-- beginAuthentication() ----->|
    |                                 |   generate challenge
    |                                 |   allowCredentials (credentialIds)
    |<-- { challenge, allowCredentials }                              |
    |                                 |                               |
    [User touches security key / biometric]                          |
    |   (signs challenge with private key)                           |
    |-- complete authentication ---- -->|                              |
    |   (authenticatorData, signature)|-- completeAuthentication() ->|
    |                                 |   verify signature against pubKey
    |                                 |   check signCount (anti-cloning)
    |                                 |   update signCount on success
    |<-- { verified: true } ----------|                               |
```

---

## Key Data Structures

### Registration

```java
// Server generates these:
PublicKeyCredentialCreationOptions creationOptions =
    PublicKeyCredentialCreationOptionsBuilder.create()
        .rp(new RelyingPartyIdentity(rpId, rpName))
        .user(new PublicKeyCredentialUserEntity(userId, displayName))
        .challenge(challenge)  // 32+ bytes from SecureRandom
        .pubKeyCredParams(List.of(
            // RS256 (RSA with SHA-256) — most compatible
            new PublicKeyCredentialParameters(PUBKEY_CRED_ALG_RS256)
        ))
        .build();
```

### Authentication

```java
// Server generates these:
PublicKeyCredentialRequestOptions requestOptions =
    PublicKeyCredentialRequestOptionsBuilder.create()
        .rpId(rpId)
        .challenge(challenge)  // 32+ bytes from SecureRandom
        .allowCredentials(List.of(
            new PublicKeyCredentialDescriptor(
                credentialId,
                Collections.singletonList(rpId)
            )
        ))
        .build();
```

---

## Anti-Cloning: signCount

Each WebAuthn credential has a `signCount` field. On every authentication:

1. Server verifies the signature
2. Server checks that the `signCount` in `authenticatorData` is **greater than** the stored `signCount`
3. If valid, server updates stored `signCount` to the new value

```
If attacker clones credential:
  Cloned credential has same initial signCount
  First auth by real device increments counter
  Second auth by cloned device → stored count > presented count → REJECTED
```

This makes cloning useless — the cloned credential becomes invalid after first use by the real device.

---

## AAGUID

The **Authenticator Attestation GUID** (`aaguid`) identifies the authenticator type/model. It allows the server to:

- Determine if an authenticator is FIDO Certified
- Apply policy based on authenticator class (e.g., require phishing-resistant)
- Log authenticator type for audit

---

## Database Schema

```sql
CREATE TABLE webauthn_credential (
    credential_id      VARCHAR(512) PRIMARY KEY,  -- base64url encoded
    user_id            VARCHAR(256) NOT NULL,
    public_key_cose    BYTEA NOT NULL,              -- COSE key format
    sign_count         BIGINT NOT NULL DEFAULT 0,   -- anti-cloning counter
    aaguid             UUID NOT NULL,               -- authenticator type ID
    attestation_format VARCHAR(64),                 -- none, fido-u2f, android-safetynet, tpm
    device_type        VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

`credential_id` is the primary key. Each user can have multiple WebAuthn credentials (e.g., laptop platform credential + security key).

---

## Security Notes

- **Private key never leaves the device** — even if the server database is compromised, attackers cannot impersonate users
- **Challenge must be fresh** — generated in-memory per ceremony, never reused; must expire
- **RP ID binding** — the credential is bound to the relying party ID (domain); phishing to a different domain fails
- **signCount anti-cloning** — credential cloning is detected and blocked
- **Attestation** (optional) — proves the authenticator is genuine (FIDO Certified); `none` attestation used for privacy in some scenarios
