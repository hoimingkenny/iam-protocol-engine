package com.iam.authcore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webauthn_credential")
@Getter
@Setter
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
}
