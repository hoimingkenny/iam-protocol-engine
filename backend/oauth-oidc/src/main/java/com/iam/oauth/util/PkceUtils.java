package com.iam.oauth.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE utilities per RFC 7636.
 *
 * code_verifier:  cryptographically random string (43-128 chars)
 *                 using unreserved URL characters [A-Z] [a-z] [0-9] - . _ ~
 *
 * code_challenge: BASE64URL(SHA256(code_verifier))  for S256 method
 */
public final class PkceUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFIER_MIN_LENGTH = 43;
    private static final int VERIFIER_MAX_LENGTH = 128;

    // S256 is the only supported method per RFC 7636
    public static final String METHOD_S256 = "S256";

    private PkceUtils() {}

    /**
     * Generate a new code_verifier (RFC 7636 §4.1).
     *
     * @return 43-128 character random string from the unreserved URL character set
     */
    public static String generateCodeVerifier() {
        // 32 bytes = 43 base64url chars after encoding
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Derive the S256 code_challenge from a code_verifier (RFC 7636 §4.2).
     *
     * @param verifier  the code_verifier
     * @return BASE64URL(SHA256(verifier))
     */
    public static String deriveCodeChallenge(String verifier) {
        if (verifier == null || verifier.isEmpty()) {
            throw new IllegalArgumentException("code_verifier must not be null or empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Verify a code_verifier against a stored code_challenge (RFC 7636 §4.6).
     *
     * @param verifier   the code_verifier submitted at /token
     * @param challenge  the code_challenge stored at /authorize
     * @param method    the code_challenge_method ("S256")
     * @return true if the verifier matches the challenge
     */
    public static boolean verifyCodeChallenge(String verifier, String challenge, String method) {
        if (verifier == null || challenge == null || method == null) {
            return false;
        }
        if (!METHOD_S256.equals(method)) {
            // Plain method is not supported in production
            return false;
        }
        return deriveCodeChallenge(verifier).equals(challenge);
    }

    /**
     * Validate that a code_verifier conforms to RFC 7636 §4.1 requirements.
     *
     * @param verifier  the code_verifier to validate
     * @return true if valid
     */
    public static boolean isValidVerifier(String verifier) {
        if (verifier == null) return false;
        int len = verifier.length();
        if (len < VERIFIER_MIN_LENGTH || len > VERIFIER_MAX_LENGTH) return false;
        return verifier.matches("[A-Za-z0-9\\-._~]+");
    }

    /**
     * Validate that a code_challenge conforms to RFC 7636 §4.2 requirements.
     *
     * @param challenge  the code_challenge to validate
     * @return true if valid
     */
    public static boolean isValidChallenge(String challenge) {
        if (challenge == null) return false;
        // S256 challenge is base64url-encoded SHA-256 = 43 chars
        return challenge.matches("[A-Za-z0-9\\-_+]+") && challenge.length() == 43;
    }
}
