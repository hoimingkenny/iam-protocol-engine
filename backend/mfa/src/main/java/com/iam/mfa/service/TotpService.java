package com.iam.mfa.service;

import com.iam.authcore.entity.TotpCredential;
import com.iam.authcore.repository.TotpCredentialRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int QR_SIZE = 300;

    private final TotpCredentialRepository totpRepo;
    private final SecretGenerator secretGenerator;
    private final CodeGenerator codeGenerator;
    private final CodeVerifier codeVerifier;
    private final QrGenerator qrGenerator;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom;

    public TotpService(TotpCredentialRepository totpRepo) {
        this.totpRepo = totpRepo;

        this.secretGenerator = new DefaultSecretGenerator();
        this.codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
        this.qrGenerator = new ZxingPngQrGenerator();

        // AES-256 key for TOTP secret encryption at rest.
        // In production: load from KMS (AWS KMS, HashiCorp Vault).
        // Exactly 32 ASCII bytes for AES-256.
        byte[] keyBytes = "iam-demo-totp-enc-key-32bytes000".getBytes();
        assert keyBytes.length == 32;
        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");

        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a new TOTP secret for the user, stores it (unverified),
     * and returns the provisioning URI + QR code image.
     *
     * @param userId  the OAuth subject (sub) of the user
     * @param issuer  human-readable issuer name for the authenticator app
     * @return SetupResult with provisioningUri and qrCodeImage (Base64 PNG)
     */
    @Transactional
    public TotpSetupResult generateSetup(String userId, String issuer) {
        // Generate a 160-bit (20-byte) Base32-encoded secret per RFC 6238
        String secret = secretGenerator.generate();

        // Encrypt secret for storage
        byte[] encryptedSecret = encrypt(secret);

        // Upsert: delete any existing credential first (enrollment is idempotent)
        totpRepo.deleteByUserId(userId);

        TotpCredential cred = new TotpCredential();
        cred.setUserId(userId);
        cred.setSecretEncrypted(encryptedSecret);
        cred.setVerified(false);
        totpRepo.save(cred);

        // Build QR data using the library's QrData builder
        QrData qrData = new QrData.Builder()
            .secret(secret)
            .issuer(issuer)
            .label(userId)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();

        // Generate QR code PNG
        String qrCodeImage;
        try {
            byte[] pngBytes = qrGenerator.generate(qrData);
            qrCodeImage = Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            throw new IllegalStateException("QR code generation failed", e);
        }

        log.info("TOTP setup initiated for userId={}", userId);
        return new TotpSetupResult(secret, qrData.getUri(), qrCodeImage);
    }

    /**
     * Verifies the provided TOTP code against the user's stored secret.
     * On first successful verification, marks the credential as verified.
     *
     * @param userId  the subject
     * @param code    6-digit TOTP code from the authenticator app
     * @return true if verified, false otherwise
     */
    @Transactional
    public boolean verify(String userId, String code) {
        if (code == null || code.length() != 6) {
            return false;
        }

        Optional<TotpCredential> opt = totpRepo.findByUserId(userId);
        if (opt.isEmpty()) {
            log.warn("TOTP verify attempt for unknown userId={}", userId);
            return false;
        }

        TotpCredential cred = opt.get();
        String secret = decrypt(cred.getSecretEncrypted());

        boolean valid = codeVerifier.isValidCode(secret, code);

        if (valid && !cred.getVerified()) {
            cred.setVerified(true);
            totpRepo.save(cred);
            log.info("TOTP verified and activated for userId={}", userId);
        } else if (!valid) {
            log.warn("TOTP verification failed for userId={}", userId);
        }

        return valid;
    }

    /**
     * Checks whether the user has a verified TOTP credential enrolled.
     */
    public boolean isEnrolled(String userId) {
        return totpRepo.findByUserId(userId)
            .map(TotpCredential::getVerified)
            .orElse(false);
    }

    // --- AES-GCM encryption (secret stored encrypted at rest) ---

    private byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    private String decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec);

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    // --- Result record ---

    public record TotpSetupResult(
        String secret,
        String provisioningUri,
        String qrCodeImage  // Base64-encoded PNG
    ) {}
}
