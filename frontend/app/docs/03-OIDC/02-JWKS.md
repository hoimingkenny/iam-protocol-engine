---
title: JWKS — JSON Web Key Set
sidebar_position: 3
description: RFC 7517 key publication for RS256 signature verification.
---

# JWKS — JSON Web Key Set

## What It Is

JWKS (RFC 7517) is a JSON document containing the server's public keys. Any client that receives an ID token signed with RS256 needs the matching public key to verify the signature. The JWKS endpoint is how the server publishes those keys.

```
GET /.well-known/jwks.json
```

## Example Response

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "qx1ejh4ylo3ip-l1koeuuekvhnse9julzu7u8i_7ymg",
      "n": "o5B1feVqa_rJjK1V6A9fp7IhaMfSgcLjDEy5RtVgOpd...",
      "e": "AQAB"
    }
  ]
}
```

## Fields Explained

**`kty`** (key type) — `RSA` in our case. OIDC supports `RSA` and `EC`. We use RSA exclusively.

**`use`** (public key use) — `sig` means this key is for signing/verification. A key with `use=enc` would be for encryption. Never use a signing key for encryption or vice versa.

**`alg`** (algorithm) — `RS256` means RSA Signature with SHA-256 (RSASSA-PKCS1-v1_5 + SHA-256). This is the required algorithm for OIDC ID tokens. Clients must reject tokens signed with any other algorithm.

**`kid`** (key ID) — the identifier for this specific key. When the server signs a token, it embeds the `kid` in the token's header so clients know which key from JWKS to use for verification. Multiple keys can coexist; the `kid` tells clients which one to pick.

**`n` and `e`** — the RSA public key components:
- `n` is the modulus (Base64URL-encoded)
- `e` is the public exponent (always `AQAB` = 65537 for our keys)

## How the kid Is Derived

The `kid` is not a random string — it's the SHA-256 thumbprint of the X.509 certificate wrapping the public key, Base64URL-encoded without padding.

```
kid = BASE64URL( SHA-256( DER-encoded-certificate ) )
```

This is deterministic: the same key always produces the same `kid`. This means:
- A client can cache the JWKS response indefinitely
- If the key is rotated, the new key has a new `kid` — no ambiguity about which key is which
- The `kid` proves the key hasn't been tampered with (changing the key changes the thumbprint)

## Key Rotation

```
POST /.well-known/jwks.json
```

Triggers key rotation: generates a new RSA key pair, adds it to the keystore, and returns the new `kid`. The old key(s) remain in JWKS so tokens signed with them continue to validate.

After rotation, JWKS returns multiple keys:

```json
{
  "keys": [
    { "kid": "old-kid-here", "kty": "RSA", ... },
    { "kid": "new-kid-here", "kty": "RSA", ... }
  ]
}
```

Clients look up by `kid` and try all keys until one works. This is deliberate — it allows zero-downtime key rotation without invalidating in-flight tokens.

## Key Storage

Keys are stored in a PKCS#12 (`.p12`) keystore file on the server filesystem:

```
/tmp/iam-engine/keystore.p12
```

The keystore contains:
- The RSA private key (for signing)
- A self-signed X.509 certificate (provides the public key + thumbprint)

The keystore password is configured via `iam.keystore.password` in `application.properties`. In production, this file must be owned by the application user with `0600` permissions — anyone with access to this file can forge tokens.

## Verification

```bash
# Get JWKS
curl http://localhost:8080/.well-known/jwks.json | jq .

# Trigger key rotation and see both keys
curl -X POST http://localhost:8080/.well-known/jwks.json | jq .
curl http://localhost:8080/.well-known/jwks.json | jq '.keys | length'  # should be 2
```
