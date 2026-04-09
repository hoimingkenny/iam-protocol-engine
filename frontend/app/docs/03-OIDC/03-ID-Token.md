---
title: ID Token
sidebar_position: 4
description: OIDC Core 1.0 ID Token — RS256 JWT structure and required claims.
---

# ID Token

## What It Is

The ID token is a signed JWT (JSON Web Token) that encodes the authenticated user's identity. Unlike the OAuth 2.0 access token — which is an opaque random string stored in the database — the ID token is self-contained: any party with the server's public key can verify it without calling the server.

The ID token is returned alongside the access token at the `/token` endpoint when the `openid` scope is requested.

## JWT Structure

A JWT has three Base64URL-encoded parts joined by dots (`.`):

```
BASE64URL(header) . BASE64URL(payload) . BASE64URL(signature)
```

Example:
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.
eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJzdWIiOiJ1c2VyLTAwMSIsImF1ZCI6InRlc3QtY2xpZW50IiwiaWF0IjoxNzc1NjU4ODM5LCJleHAiOjE3NzU2NjI0Mzl9.
fOa2DnO8q70jKHNlZTLJEdbt72vmySmT4YYPLtsxJhIVDE9jyWvOavTxY4hzPeF41KaY-QzRAHx0FW5XgWUyVUcGk5xaqW1pnYGwQUuZB_m-2G2BEVEP8VTKLJKFgjSmFUBMvVVhLpX2gte3Ess7CXeaVhQGlxLoKkAVTWqXufzqznCiKPA46YBBUmL4g6--o_1c7iFvRRGM7_94KP_Kyc8CuTnrre2VA6WBJmf1_m9M-8dzMOexOjZWmRRw4gWpMx6F-9zezL4hSdx2cMN9OmJzGPTiHeGK2qj5Xzr4Qc8dJFx6vqxeHAw8psfjxNoGSFELrVr9oOH0ynyo492Mew
```

## Decoding the Token

```bash
# Header (algorithm and type)
echo "$ID_TOKEN" | cut -d. -f1 | base64 -d
# {"alg":"RS256","typ":"JWT"}

# Payload (claims)
echo "$ID_TOKEN" | cut -d. -f2 | base64 -d
# {"iss":"http://localhost:8080","sub":"user-001","aud":"test-client",
#   "iat":1775658839,"exp":1775662439}

# Signature — verified against JWKS public key
```

## Required Claims

OIDC Core 1.0 §2 specifies these required claims in every ID token:

| Claim | Meaning | Example |
|-------|---------|---------|
| `iss` | Issuer identifier — must match discovery `issuer` | `http://localhost:8080` |
| `sub` | Subject — the user identifier (pairwise for privacy) | `user-001` |
| `aud` | Audience — the client_id of the relying party | `test-client` |
| `iat` | Issued at — Unix timestamp | `1775658839` |
| `exp` | Expiration — Unix timestamp | `1775662439` |

**OIDC Core 1.0 §2 ID Token**: the `sub` claim is the only REQUIRED user identifier. Everything else is optional — but without `iss` and `aud` the token cannot be validated, so in practice they're always present.

## Optional Claims

| Claim | When Present | Source |
|-------|--------------|--------|
| `nonce` | Requested in `/authorize` with `nonce` param | Auth code's stored nonce |

## RS256 — How Signature Verification Works

RS256 (RSA + SHA-256) is an asymmetric algorithm:

1. **Signing (server side)** — the server hashes the header+payload with SHA-256, then encrypts the hash with the RSA private key (PKCS#1 v1.5 padding)
2. **Verification (client side)** — the client decrypts the signature using the RSA public key from JWKS, re-hashes the header+payload, and compares

The client never has the private key — only the public key. Anyone can verify; only the server can sign.

## Why Not HS256?

HS256 (HMAC with a shared secret) is simpler but requires the client to hold the server's signing secret. For a portfolio demo this is fine, but in production it means:
- Every client that can verify tokens can also forge tokens
- Compromising one client compromises the entire system
- Key rotation requires rotating the shared secret with every client

RS256 avoids all of this: the signing key never leaves the server, and key rotation doesn't require touching any client.

## Nonce Validation

The `nonce` claim is a security measure against replay attacks. The flow:

```
1. Client generates random nonce, sends in /authorize request
2. Server stores nonce in auth code record
3. Server echoes nonce in ID token at /token exchange
4. Client verifies the nonce in the ID token matches what it sent in step 1
```

If an attacker intercepts the ID token and tries to replay it, the nonce won't match — the client will reject it.

## Token Lifetime

The ID token has a 1-hour TTL (`exp = iat + 3600`). This is shorter than the access token's 1-hour TTL by design — the ID token is more sensitive (contains identity) and should expire sooner. The refresh token handles session continuation.

## Verification Checklist

A client library validating an ID token must:
- [ ] Fetch JWKS from `jwks_uri` in discovery document
- [ ] Extract `kid` from the token header
- [ ] Find the matching key in JWKS by `kid`
- [ ] Verify the signature using that public key and RS256
- [ ] Verify `iss` matches the discovery `issuer`
- [ ] Verify `aud` contains this client's `client_id`
- [ ] Verify `exp` is in the future
- [ ] Verify `iat` is not in the future (clock skew tolerance: ±5 min)
- [ ] Verify `nonce` matches if one was sent in the original `/authorize` request

## Verification

```bash
# Get an ID token via Auth Code + PKCE
# (see 02-OAuth2/01-PKCE.md for the full flow)
curl http://localhost:8080/.well-known/jwks.json | jq '.keys[0]'  # get public key
# Decode the token
echo "$ID_TOKEN" | cut -d. -f2 | base64 -d | jq .
```
