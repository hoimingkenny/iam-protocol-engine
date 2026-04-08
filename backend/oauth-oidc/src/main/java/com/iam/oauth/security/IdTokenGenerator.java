package com.iam.oauth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.util.*;

/**
 * Generates OIDC ID Tokens as RS256-signed JWTs (JWS Compact Serialization).
 *
 * ID Token per OIDC Core 1.0 §2 and ID Token spec §3.1.
 * Required claims: iss, sub, aud, exp, iat, nonce.
 *
 * The JWT is built manually (no library) to demonstrate RFC-level understanding:
 *   JWS = BASE64URL(header) || '.' || BASE64URL(payload) || '.' || BASE64URL(signature)
 *
 * Signing: RSASSA-PKCS1-v1_5 with SHA-256 (RS256) using the current RSA private key.
 */
@Component
public class IdTokenGenerator {

    private static final String ALGORITHM_HEADER = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    private static final long ID_TOKEN_TTL_SECONDS = 3600; // 1 hour

    private final JwksService jwksService;
    private final String issuer;

    public IdTokenGenerator(JwksService jwksService,
                            @Value("${server.port:8080}") int port) {
        this.jwksService = jwksService;
        this.issuer = "http://localhost:" + port;
    }

    /**
     * Generate an RS256 ID Token JWT.
     *
     * @param subject  The user subject (sub claim)
     * @param clientId The audience (aud claim — client_id of the relying party)
     * @param nonce    The nonce from the original /authorize request (may be null)
     * @return Base64URL-encoded signed JWT string
     */
    public String generateIdToken(String subject, String clientId, String nonce) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ID_TOKEN_TTL_SECONDS);

        // Build payload claims
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", subject);
        payload.put("aud", clientId);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiry.getEpochSecond());
        if (nonce != null && !nonce.isBlank()) {
            payload.put("nonce", nonce);
        }

        return createSignedJwt(payload);
    }

    /**
     * Build a JWS Compact Serialization token:
     *   BASE64URL( UTF-8(header) ) . BASE64URL( UTF-8(payload) ) . BASE64URL( signature )
     */
    private String createSignedJwt(Map<String, Object> payload) {
        try {
            // Step 1: encode header
            String headerB64 = base64UrlEncode(ALGORITHM_HEADER.getBytes(StandardCharsets.UTF_8));

            // Step 2: encode payload
            String payloadJson = toJson(payload);
            String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            // Step 3: signing input = headerB64 || '.' || payloadB64
            String signingInput = headerB64 + "." + payloadB64;
            byte[] signingInputBytes = signingInput.getBytes(StandardCharsets.UTF_8);

            // Step 4: sign with RS256 (RSA + SHA-256)
            byte[] signature = signRs256(signingInputBytes);

            // Step 5: assemble JWS compact serialization
            String signatureB64 = base64UrlEncode(signature);
            return signingInput + "." + signatureB64;

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign ID token", e);
        }
    }

    /**
     * RSASSA-PKCS1-v1_5 with SHA-256 using the current RSA private key.
     */
    private byte[] signRs256(byte[] data) throws GeneralSecurityException {
        // Get current private key from keystore
        RSAPrivateCrtKey privateKey = jwksService.getCurrentPrivateKey()
            .orElseThrow(() -> new IllegalStateException("No RSA private key available"));

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey, new SecureRandom());
        sig.update(data);
        return sig.sign();
    }

    /**
     * Base64-URL encode without padding (RFC 7515 §2).
     */
    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Minimal JSON serializer — handles only String, Number, Boolean, List, Map.
     * No external library needed for a portfolio demo's narrow use case.
     */
    private static String toJson(Map<String, Object> obj) {
        StringBuilder sb = new StringBuilder("{");
        var entries = obj.entrySet().iterator();
        while (entries.hasNext()) {
            var e = entries.next();
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append("\"").append(escapeJson(s)).append("\"");
            } else if (v instanceof Number n) {
                sb.append(n.toString());
            } else if (v instanceof Boolean b) {
                sb.append(b.toString());
            } else if (v instanceof List<?> l) {
                sb.append("[");
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object item = l.get(i);
                    if (item instanceof String si) {
                        sb.append("\"").append(escapeJson(si)).append("\"");
                    } else {
                        sb.append(item.toString());
                    }
                }
                sb.append("]");
            } else {
                sb.append("\"").append(escapeJson(v.toString())).append("\"");
            }
            if (entries.hasNext()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
