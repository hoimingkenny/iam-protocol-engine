package com.iam.authcore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_code")
@Getter
@Setter
public class DeviceCode {

    public enum Status {
        pending, approved, denied, expired
    }

    @Id
    @Column(name = "device_code", length = 128)
    private String deviceCode;

    @Column(name = "user_code", length = 16, nullable = false, unique = true)
    private String userCode;

    @Column(name = "client_id", length = 128, nullable = false)
    private String clientId;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.pending;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "approved_by", length = 256)
    private String approvedBy;

    @Column(name = "polling_count", nullable = false)
    private Integer pollingCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
