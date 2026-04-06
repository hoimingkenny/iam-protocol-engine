package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "directory_link")
public class DirectoryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", length = 256, nullable = false)
    private String userId;

    @Column(name = "directory_source", length = 64, nullable = false)
    private String directorySource;

    @Column(name = "directory_dn", length = 1024)
    private String directoryDn;

    @Column(name = "directory_groups", columnDefinition = "TEXT")
    private String directoryGroups;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.syncedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDirectorySource() { return directorySource; }
    public void setDirectorySource(String source) { this.directorySource = source; }
    public String getDirectoryDn() { return directoryDn; }
    public void setDirectoryDn(String dn) { this.directoryDn = dn; }
    public String getDirectoryGroups() { return directoryGroups; }
    public void setDirectoryGroups(String groups) { this.directoryGroups = groups; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
