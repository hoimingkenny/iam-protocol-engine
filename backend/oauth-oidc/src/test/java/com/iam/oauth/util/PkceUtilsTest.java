package com.iam.oauth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PkceUtilsTest {

    // RFC 7636 Appendix B test vector
    // code_verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    // expected S256 challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

    @Test
    void deriveCodeChallenge_rfc7636AppendixBVector() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        assertEquals(expected, PkceUtils.deriveCodeChallenge(verifier));
    }

    @Test
    void verifyCodeChallenge_validS256Match_returnsTrue() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        assertTrue(PkceUtils.verifyCodeChallenge(verifier, challenge, "S256"));
    }

    @Test
    void verifyCodeChallenge_mismatchedVerifier_returnsFalse() {
        String wrongVerifier = "wrong_verifier_not_matching_challenge_at_all";
        String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        assertFalse(PkceUtils.verifyCodeChallenge(wrongVerifier, challenge, "S256"));
    }

    @Test
    void verifyCodeChallenge_nullInputs_returnsFalse() {
        assertFalse(PkceUtils.verifyCodeChallenge(null, "challenge", "S256"));
        assertFalse(PkceUtils.verifyCodeChallenge("verifier", null, "S256"));
        assertFalse(PkceUtils.verifyCodeChallenge("verifier", "challenge", null));
    }

    @Test
    void verifyCodeChallenge_unsupportedMethod_returnsFalse() {
        assertFalse(PkceUtils.verifyCodeChallenge("verifier", "challenge", "plain"));
    }

    @Test
    void generateCodeVerifier_lengthIs43() {
        // Base64URL-encoded 32 bytes = 43 chars
        String verifier = PkceUtils.generateCodeVerifier();
        assertEquals(43, verifier.length());
    }

    @Test
    void generateCodeVerifier_containsOnlyValidChars() {
        String verifier = PkceUtils.generateCodeVerifier();
        assertTrue(verifier.matches("[A-Za-z0-9\\-._~]+"),
            "verifier should only contain unreserved URL characters");
    }

    @Test
    void generateCodeVerifier_deterministicChallengeRoundTrip() {
        String verifier = PkceUtils.generateCodeVerifier();
        String challenge = PkceUtils.deriveCodeChallenge(verifier);
        assertTrue(PkceUtils.verifyCodeChallenge(verifier, challenge, "S256"));
    }

    @Test
    void isValidVerifier_validLength_returnsTrue() {
        assertTrue(PkceUtils.isValidVerifier(PkceUtils.generateCodeVerifier()));
    }

    @Test
    void isValidVerifier_tooShort_returnsFalse() {
        assertFalse(PkceUtils.isValidVerifier("too-short"));
    }

    @Test
    void isValidVerifier_tooLong_returnsFalse() {
        String longVerifier = "a".repeat(129);
        assertFalse(PkceUtils.isValidVerifier(longVerifier));
    }

    @Test
    void isValidVerifier_invalidChars_returnsFalse() {
        assertFalse(PkceUtils.isValidVerifier("not valid!@#$%"));
    }

    @Test
    void isValidVerifier_null_returnsFalse() {
        assertFalse(PkceUtils.isValidVerifier(null));
    }

    @Test
    void isValidChallenge_validS256_returnsTrue() {
        // 43 chars, base64url alphabet
        assertTrue(PkceUtils.isValidChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
    }

    @Test
    void isValidChallenge_wrongLength_returnsFalse() {
        assertFalse(PkceUtils.isValidChallenge("too-short"));
        assertFalse(PkceUtils.isValidChallenge("a".repeat(44)));
    }

    @Test
    void isValidChallenge_invalidChars_returnsFalse() {
        assertFalse(PkceUtils.isValidChallenge("not valid!@#$%^&*()"));
    }

    @Test
    void isValidChallenge_null_returnsFalse() {
        assertFalse(PkceUtils.isValidChallenge(null));
    }

    @Test
    void deriveCodeChallenge_nullVerifier_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PkceUtils.deriveCodeChallenge(null));
    }

    @Test
    void deriveCodeChallenge_emptyVerifier_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> PkceUtils.deriveCodeChallenge(""));
    }
}
