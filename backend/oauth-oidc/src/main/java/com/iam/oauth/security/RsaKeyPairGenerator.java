package com.iam.oauth.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Generates and persists an RSA key pair in a PKCS12 keystore file.
 *
 * Key identity (kid) is derived from the X.509 certificate's SHA-256 thumbprint
 * (RFC 7517 §4.5) — Base64-URL encoded without padding. Stable across restarts.
 *
 * On startup, JwksService calls {@link #getOrCreateKeyPair()}. If the keystore
 * file exists it is loaded; otherwise a new key pair is generated and persisted.
 *
 * Key rotation: call {@link #addNewKeyToKeystore()} to generate a new key.
 * Old keys remain in the keystore and are served via JWKS for token validation.
 */
@Component
public class RsaKeyPairGenerator {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int DEFAULT_KEY_SIZE = 2048;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final Path keystorePath;
    private final char[] keystorePassword;
    private final int keySize;

    public RsaKeyPairGenerator(
            @Value("${iam.keystore.path:/tmp/iam-engine/keystore.p12}") String keystorePath,
            @Value("${iam.keystore.password:iam-engine-ks-pass}") String keystorePassword,
            @Value("${iam.rsa.key.size:2048}") int keySize) {
        this.keystorePath = Path.of(keystorePath);
        this.keystorePassword = keystorePassword.toCharArray();
        this.keySize = keySize;
    }

    /**
     * Load existing key pair from keystore, or generate and persist a new one.
     * The keystore alias equals the kid so the same key maps to the same alias.
     */
    public KeyStore getOrCreateKeyPair() {
        try {
            Files.createDirectories(keystorePath.getParent());

            if (Files.exists(keystorePath)) {
                KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
                try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                    ks.load(fis, keystorePassword);
                }
                if (ks.aliases().hasMoreElements()) {
                    return ks;
                }
            }
            return generateAndStore();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load or create keystore", e);
        }
    }

    /**
     * Generate a new RSA key pair wrapped in a self-signed X.509 certificate,
     * compute its kid, and store in the keystore. Returns the new kid.
     */
    public String addNewKeyToKeystore() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KEY_ALGORITHM, "BC");
        gen.initialize(keySize, new SecureRandom());
        KeyPair keyPair = gen.generateKeyPair();

        X509Certificate cert = generateSelfSignedCert(keyPair, "CN=iam-protocol-engine");
        String kid = computeKid(cert);

        KeyStore ks = getOrCreateKeyPair();
        ks.setKeyEntry(kid, keyPair.getPrivate(), keystorePassword,
            new java.security.cert.Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            ks.store(fos, keystorePassword);
        }

        return kid;
    }

    /**
     * Derives kid from the X.509 certificate's SHA-256 thumbprint (RFC 7517 §4.5).
     * Base64-URL encoded without padding — stable for the same certificate.
     */
    public String computeKid(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(cert.getEncoded());
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    public Path getKeystorePath() {
        return keystorePath;
    }

    // --- private helpers ---

    private KeyStore generateAndStore() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KEY_ALGORITHM, "BC");
        gen.initialize(keySize, new SecureRandom());
        KeyPair keyPair = gen.generateKeyPair();

        X509Certificate cert = generateSelfSignedCert(keyPair, "CN=iam-protocol-engine");
        String kid = computeKid(cert);

        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE, "BC");
        ks.load(null, null);
        ks.setKeyEntry(kid, keyPair.getPrivate(), keystorePassword,
            new java.security.cert.Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            ks.store(fos, keystorePassword);
        }

        return ks;
    }

    private X509Certificate generateSelfSignedCert(KeyPair keyPair, String cn) throws Exception {
        long nowMs = System.currentTimeMillis();
        Date notBefore = new Date(nowMs);
        Date notAfter = new Date(nowMs + 365L * 24 * 3600 * 1000);

        X500Name issuer = new X500Name(cn);
        X500Name subject = new X500Name(cn);
        BigInteger serial = BigInteger.valueOf(nowMs);

        // JcaX509v3CertificateBuilder takes PublicKey directly
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        certBuilder.addExtension(
            org.bouncycastle.asn1.x509.Extension.keyUsage, true,
            new org.bouncycastle.asn1.x509.KeyUsage(
                org.bouncycastle.asn1.x509.KeyUsage.digitalSignature |
                org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certBuilder.build(signer));
    }
}
