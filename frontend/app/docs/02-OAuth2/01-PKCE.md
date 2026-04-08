---
title: PKCE — RFC 7636 Step by Step
sidebar_position: 2
description: How PKCE protects authorization codes from interception — code_verifier, S256 challenge, and the public client attack it prevents.
---

# PKCE — RFC 7636 Step by Step

## Why PKCE Exists

Without PKCE, an authorization code can be stolen from the URL redirect if the device has a shared network or proxy. The attack:

```
1. Attacker sends auth request from device → /authorize?client_id=pub&redirect_uri=http://attacker.com/callback
2. User clicks link on attacker's device → redirected to attacker's callback with ?code=XYZ
3. Attacker takes code and calls /token immediately
```

PKCE closes this by making the token exchange require something the attacker doesn't have: the original `code_verifier` that was only on the legitimate device.

## The Two-Party Protocol

PKCE adds two parameters to the standard authorization code flow:

**At `/authorize` (GET):**
```
code_challenge = BASE64URL(SHA256(code_verifier))
code_challenge_method = S256
```

**At `/token` (POST):**
```
code_verifier = <original random string>
```

The AS stores the `code_challenge` when issuing the code, then verifies the `code_verifier` matches it before issuing tokens.

## RFC 7636 Appendix B Test Vector

This is the test vector from the RFC — use it to verify any PKCE implementation:

```
code_verifier  = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
code_challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"  (S256)
code_challenge_method = "S256"
```

If your `deriveCodeChallenge(verifier)` doesn't produce the above, the implementation is wrong.

## Implementation: PkceUtils

```java
// Generate a 43-char code_verifier
public static String generateCodeVerifier() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}

// Derive S256 challenge from verifier
public static String deriveCodeChallenge(String verifier) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(verifier.getBytes(US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
}

// Verify at token exchange
public static boolean verifyCodeChallenge(String verifier, String challenge, String method) {
    if (!METHOD_S256.equals(method)) return false;
    return deriveCodeChallenge(verifier).equals(challenge);
}
```

## Security Properties

| Property | How PKCE Provides It |
|---------|---------------------|
| Verifier is unknown to attacker | 43 random chars — infeasible to guess |
| Challenge is verifiable without secrets | SHA-256 is deterministic — verifier → challenge always produces same result |
| Method is enforced | Only `S256` accepted in production; `plain` is explicitly rejected |

## When PKCE Is Required

- **Public clients** (native apps, SPAs): PKCE is **required** per RFC 7636
- **Confidential clients** (server-side): PKCE is **recommended** but optional

The `is_public` flag on `OAuthClient` controls this in the IAM Protocol Engine:

```java
if (request.requiresPkce()) {
    if (codeVerifier == null) return error("code_verifier required for public clients");
    if (!PkceUtils.verifyCodeChallenge(codeVerifier, storedChallenge, method)) {
        return error("invalid_grant");
    }
}
```

## Test Your PKCE Implementation

```bash
VERIFIER="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
CHALLENGE=$(echo -n "$VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')

# CHALLENGE should be: E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
echo "Challenge: $CHALLENGE"
```
