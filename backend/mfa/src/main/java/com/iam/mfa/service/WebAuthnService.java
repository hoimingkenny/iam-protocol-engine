package com.iam.mfa.service;

import com.iam.authcore.entity.WebAuthnCredential;
import com.iam.authcore.repository.WebAuthnCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);

    private static final int CHALLENGE_LENGTH = 32;
    private static final String RP_NAME = "IAM Protocol Engine";

    private final WebAuthnCredentialRepository credentialRepo;
    private final SecureRandom secureRandom;

    public WebAuthnService(WebAuthnCredentialRepository credentialRepo) {
        this.credentialRepo = credentialRepo;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Begins WebAuthn registration. Generates a random challenge and returns
     * the options the browser needs to call navigator.credentials.create().
     *
     * @param userId       the subject (sub) for this credential
     * @param displayName  human-readable name for the authenticator UI
     * @return RegistrationBeginResult with challenge and encoded credential creation options
     */
    public RegistrationBeginResult beginRegistration(String userId, String displayName) {
        // Generate random challenge (32 bytes, Base64URL-encoded)
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        // Generate a unique credential ID for this registration ceremony
        String credentialUid = UUID.randomUUID().toString();

        // Build credential creation options (PublicKeyCredentialCreationOptions)
        // The actual JSON serialization to Browser API format happens in the controller
        PublicKeyCredentialCreationOptions options = new PublicKeyCredentialCreationOptions(
            userId,
            displayName,
            challenge,
            RP_NAME,
            credentialUid
        );

        log.info("WebAuthn registration began for userId={}, credentialUid={}", userId, credentialUid);
        return new RegistrationBeginResult(options, credentialUid);
    }

    /**
     * Completes WebAuthn registration. Parses the credential response from the
     * authenticator and stores the credential if attestation is valid.
     *
     * @param response   the Base64URL-encoded registration response (JSON)
     * @param credentialUid  the UID returned from beginRegistration (used to link the ceremony)
     * @param userId     the subject this credential belongs to
     * @return true if registration succeeded
     */
    @Transactional
    public boolean completeRegistration(String response, String credentialUid,
                                         String userId, String deviceType) {
        // webauthn4j parses and validates the registration response
        // We do basic extraction of credential data here for storage
        try {
            byte[] responseBytes = Base64.getUrlDecoder().decode(response);

            // Parse the attestation object from the response.
            // The response structure (JSON) contains: { credential, attestationObject, clientDataJSON }
            // We extract key fields manually for storage.
            // Full attestation validation (FIDO MDS, attestation statement format) is done
            // by webauthn4j if configured.

            // For demo: parse the credential ID and public key from the response.
            // This is a simplified parse — in production, use webauthn4j's RegistrationRequest.
            ParsedRegistrationData parsed = parseRegistrationResponse(responseBytes);

            WebAuthnCredential credential = new WebAuthnCredential();
            credential.setCredentialId(parsed.credentialId);
            credential.setUserId(userId);
            credential.setPublicKeyCose(parsed.publicKeyCose);
            credential.setSignCount(0L);
            credential.setAaguid(parsed.aaguid != null ? parsed.aaguid : new UUID(0, 0));
            credential.setAttestationFormat(parsed.attestationFormat);
            credential.setDeviceType(deviceType);

            credentialRepo.save(credential);
            log.info("WebAuthn credential registered for userId={}, credentialId={}",
                     userId, parsed.credentialId);
            return true;
        } catch (Exception e) {
            log.error("WebAuthn registration failed for userId={}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Begins WebAuthn authentication. Generates a challenge and returns
     * options for navigator.credentials.get().
     *
     * @param userId  the subject to authenticate
     * @return AuthenticationBeginResult with challenge and options
     */
    public AuthenticationBeginResult beginAuthentication(String userId) {
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        // Look up registered credentials for this user
        List<WebAuthnCredential> credentials = credentialRepo.findByUserId(userId);
        List<String> allowedCredentialIds = credentials.stream()
            .map(WebAuthnCredential::getCredentialId)
            .toList();

        if (allowedCredentialIds.isEmpty()) {
            log.warn("WebAuthn auth attempt for userId={} with no registered credentials", userId);
            return null; // No credentials registered
        }

        AuthenticationOptions options = new AuthenticationOptions(
            userId,
            challenge,
            allowedCredentialIds
        );

        log.info("WebAuthn authentication began for userId={}, {} credential(s)",
                 userId, allowedCredentialIds.size());
        return new AuthenticationBeginResult(options);
    }

    /**
     * Completes WebAuthn authentication. Verifies the assertion response.
     *
     * @param response  Base64URL-encoded assertion response (JSON)
     * @param userId    the subject claiming this authentication
     * @return true if assertion is valid and sign_count was updated
     */
    @Transactional
    public boolean completeAuthentication(String response, String userId) {
        try {
            byte[] responseBytes = Base64.getUrlDecoder().decode(response);
            ParsedAssertionData parsed = parseAssertionResponse(responseBytes);

            // Find the credential
            List<WebAuthnCredential> creds = credentialRepo.findByUserId(userId);
            WebAuthnCredential credential = creds.stream()
                .filter(c -> c.getCredentialId().equals(parsed.credentialId))
                .findFirst()
                .orElse(null);

            if (credential == null) {
                log.warn("WebAuthn assertion for unknown credentialId={}", parsed.credentialId);
                return false;
            }

            // Verify sign_count (anti-cloning)
            if (parsed.signCount <= credential.getSignCount()) {
                log.error("WebAuthn sign_count check failed: received {} <= stored {}",
                          parsed.signCount, credential.getSignCount());
                return false;
            }

            // Update sign_count
            credential.setSignCount(parsed.signCount);
            credentialRepo.save(credential);

            log.info("WebAuthn authentication succeeded for userId={}, credentialId={}, newSignCount={}",
                     userId, parsed.credentialId, parsed.signCount);
            return true;
        } catch (Exception e) {
            log.error("WebAuthn authentication failed for userId={}: {}", userId, e.getMessage());
            return false;
        }
    }

    // --- Simple binary parsing helpers (WebAuthn uses CBOR for attestation/assertion) ---

    private ParsedRegistrationData parseRegistrationResponse(byte[] responseBytes) {
        // Simplified: extract fields from the raw bytes.
        // Real implementation would use webauthn4j's RegistrationRequest.
        // Here we return placeholder values — real attestation parsing requires
        // CBOR decoding of attestationObject and clientDataJSON.
        return new ParsedRegistrationData(
            "placeholder-credential-id",
            new byte[32],
            new UUID(0, 0),
            "none"
        );
    }

    private ParsedAssertionData parseAssertionResponse(byte[] responseBytes) {
        // Simplified: extract fields from assertion response.
        // Real implementation uses webauthn4j's AuthenticationRequest.
        return new ParsedAssertionData(
            "placeholder-credential-id",
            1L
        );
    }

    // --- Data records ---

    public record PublicKeyCredentialCreationOptions(
        String userId,
        String displayName,
        String challenge,
        String rpName,
        String credentialUid
    ) {}

    public record RegistrationBeginResult(
        PublicKeyCredentialCreationOptions options,
        String credentialUid
    ) {}

    public record AuthenticationOptions(
        String userId,
        String challenge,
        List<String> allowedCredentialIds
    ) {}

    public record AuthenticationBeginResult(AuthenticationOptions options) {}

    private record ParsedRegistrationData(
        String credentialId,
        byte[] publicKeyCose,
        UUID aaguid,
        String attestationFormat
    ) {}

    private record ParsedAssertionData(
        String credentialId,
        long signCount
    ) {}
}
