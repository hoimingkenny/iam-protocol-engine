package com.iam.mfa.service;

import com.iam.authcore.entity.TotpCredential;
import com.iam.authcore.repository.TotpCredentialRepository;
import com.iam.mfa.service.TotpService.TotpSetupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TotpServiceTest {

    private TotpCredentialRepository totpRepo;
    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpRepo = mock(TotpCredentialRepository.class);
        totpService = new TotpService(totpRepo);
    }

    @Test
    void generateSetup_returnsSecretAndQrData() {
        TotpSetupResult result = totpService.generateSetup("alice", "IAMDemo");

        assertNotNull(result.secret());
        assertTrue(result.secret().length() >= 16); // Base32-encoded secret
        assertTrue(result.provisioningUri().startsWith("otpauth://totp/"));
        assertTrue(result.provisioningUri().contains("secret="));
        assertFalse(result.qrCodeImage().isBlank()); // Base64 PNG

        verify(totpRepo).deleteByUserId("alice");
        verify(totpRepo).save(any(TotpCredential.class));
    }

    @Test
    void generateSetup_isIdempotent() {
        totpService.generateSetup("alice", "IAMDemo");
        totpService.generateSetup("alice", "IAMDemo");

        // Should delete and re-save (idempotent enrollment)
        verify(totpRepo, times(2)).deleteByUserId("alice");
        verify(totpRepo, times(2)).save(any(TotpCredential.class));
    }

    @Test
    void isEnrolled_returnsFalse_whenNoCredential() {
        when(totpRepo.findByUserId("bob")).thenReturn(Optional.empty());
        assertFalse(totpService.isEnrolled("bob"));
    }

    @Test
    void isEnrolled_returnsTrue_whenVerified() {
        TotpCredential cred = new TotpCredential();
        cred.setUserId("carol");
        cred.setVerified(true);
        when(totpRepo.findByUserId("carol")).thenReturn(Optional.of(cred));

        assertTrue(totpService.isEnrolled("carol"));
    }

    @Test
    void verify_returnsFalse_whenNoCredential() {
        when(totpRepo.findByUserId("unknown")).thenReturn(Optional.empty());
        assertFalse(totpService.verify("unknown", "123456"));
    }

    @Test
    void verify_returnsFalse_whenCodeWrongLength() {
        assertFalse(totpService.verify("alice", "12345"));   // too short
        assertFalse(totpService.verify("alice", "1234567")); // too long
        assertFalse(totpService.verify("alice", null));      // null
        verifyNoInteractions(totpRepo);
    }
}
