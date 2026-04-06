package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webauthn_credential")
public class WebAuthnCredential {

    @Id
    @Column(name = "credential_id", length = 512)
    private String credentialId;

    @Column(name = "user_id", length = 256, nullable = false)
    private String userId;

    @Column(name = "public_key_cose", columnDefinition = "bytea", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private Long signCount = 0L;

    @Column(name = "aaguid", nullable = false)
    private UUID aaguid;

    @Column(name = "attestation_format", length = 64)
    private String attestationFormat;

    @Column(name = "device_type", length = 128)
    private String deviceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public byte[] getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(byte[] key) { this.publicKeyCose = key; }
    public Long getSignCount() { return signCount; }
    public void setSignCount(Long signCount) { this.signCount = signCount; }
    public UUID getAaguid() { return aaguid; }
    public void setAaguid(UUID aaguid) { this.aaguid = aaguid; }
    public String getAttestationFormat() { return attestationFormat; }
    public void setAttestationFormat(String format) { this.attestationFormat = format; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
