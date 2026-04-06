package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_code")
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

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getUserCode() { return userCode; }
    public void setUserCode(String code) { this.userCode = code; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Integer getPollingCount() { return pollingCount; }
    public void setPollingCount(Integer count) { this.pollingCount = count; }
    public Instant getCreatedAt() { return createdAt; }
}
