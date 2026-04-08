package com.iam.oauth.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/**
 * Provides RSA key pairs from the PKCS12 keystore and exposes them in JWKS format.
 *
 * Each key is stored in the keystore with alias = kid (Base64-URL thumbprint of cert).
 * At startup the current key is loaded; additional keys can be added via key rotation.
 *
 * All keys (current and historical) are served via JWKS so existing tokens
 * signed with old keys can still be validated.
 */
@Service
public class JwksService {

    private final RsaKeyPairGenerator keyGenerator;
    private final char[] keystorePassword;

    private KeyStore keystore;

    /** Ordered list of kids (most recent first). */
    private final List<String> kidOrder = new ArrayList<>();

    public JwksService(RsaKeyPairGenerator keyGenerator,
                        @Value("${iam.keystore.password:iam-engine-ks-pass}") String keystorePassword) {
        this.keyGenerator = keyGenerator;
        this.keystorePassword = keystorePassword.toCharArray();
    }

    @PostConstruct
    public void init() {
        this.keystore = keyGenerator.getOrCreateKeyPair();
        try {
            loadKidOrder();
        } catch (java.security.KeyStoreException e) {
            throw new IllegalStateException("Failed to load keystore aliases", e);
        }
    }

    private void loadKidOrder() throws java.security.KeyStoreException {
        kidOrder.clear();
        var aliases = keystore.aliases();
        while (aliases.hasMoreElements()) {
            kidOrder.add(aliases.nextElement());
        }
    }

    /**
     * Returns the current (most recently added) RSA public key as a JWK map.
     * Contains: kid, kty, use, alg, n, e.
     */
    public Map<String, Object> getCurrentKeyJwk() {
        if (kidOrder.isEmpty()) {
            throw new IllegalStateException("No keys in keystore");
        }
        return getKeyJwk(kidOrder.get(kidOrder.size() - 1));
    }

    /**
     * Returns all keys in JWKS format (RFC 7517 §5).
     * Ordered oldest-first so clients prefer the newest key (last in array).
     */
    public List<Map<String, Object>> getAllJwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        for (String kid : kidOrder) {
            keys.add(getKeyJwk(kid));
        }
        return keys;
    }

    /**
     * Build a JWKS JSON structure suitable for /.well-known/jwks.json.
     */
    public Map<String, Object> buildJwksResponse() {
        return Map.of("keys", getAllJwks());
    }

    /**
     * Returns the RSAPublicKey for a given kid, or empty if not found.
     */
    public Optional<RSAPublicKey> getPublicKey(String kid) {
        try {
            Certificate cert = keystore.getCertificate(kid);
            if (cert == null) return Optional.empty();
            PublicKey pk = cert.getPublicKey();
            if (pk instanceof RSAPublicKey) {
                return Optional.of((RSAPublicKey) pk);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the RSAPrivateKey for a given kid, or empty if not found.
     * Used for token signing (server-side only — never expose private keys).
     */
    public Optional<RSAPrivateCrtKey> getPrivateKey(String kid) {
        try {
            Key key = keystore.getKey(kid, keystorePassword);
            if (key instanceof RSAPrivateCrtKey) {
                return Optional.of((RSAPrivateCrtKey) key);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the most recent (current) private key.
     */
    public Optional<RSAPrivateCrtKey> getCurrentPrivateKey() {
        if (kidOrder.isEmpty()) return Optional.empty();
        return getPrivateKey(kidOrder.get(kidOrder.size() - 1));
    }

    /**
     * Generate a new RSA key pair and add it to the keystore.
     * Returns the new kid. Old keys remain for token validation.
     */
    public String rotateKey() throws Exception {
        String newKid = keyGenerator.addNewKeyToKeystore();
        keystore = keyGenerator.getOrCreateKeyPair();
        loadKidOrder();
        return newKid;
    }

    /**
     * Returns all known kids (for token validation against any valid key).
     */
    public List<String> getAllKids() {
        return Collections.unmodifiableList(kidOrder);
    }

    // --- private helpers ---

    private Map<String, Object> getKeyJwk(String kid) {
        try {
            Certificate cert = keystore.getCertificate(kid);
            if (cert == null) {
                throw new IllegalStateException("No certificate for kid: " + kid);
            }
            PublicKey pk = cert.getPublicKey();
            if (!(pk instanceof RSAPublicKey rsaPk)) {
                throw new IllegalStateException("Key for kid " + kid + " is not RSA");
            }

            return buildRsaJwk(rsaPk, kid);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JWK for kid: " + kid, e);
        }
    }

    /**
     * Builds a JWK map for an RSA public key per RFC 7518 §6.3.1.
     */
    private Map<String, Object> buildRsaJwk(RSAPublicKey pk, String kid) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", base64UrlEncode(pk.getModulus().toByteArray()));
        jwk.put("e", base64UrlEncode(pk.getPublicExponent().toByteArray()));
        return jwk;
    }

    /**
     * Base64-URL encode a byte array without padding (RFC 7515 §2).
     */
    private static String base64UrlEncode(byte[] data) {
        // Remove leading zero byte if present (for positive BigInteger)
        byte[] stripped = stripLeadingZero(data);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stripped);
    }

    private static byte[] stripLeadingZero(byte[] data) {
        if (data.length > 1 && data[0] == 0) {
            return Arrays.copyOfRange(data, 1, data.length);
        }
        return data;
    }
}
