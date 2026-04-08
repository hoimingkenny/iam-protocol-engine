package com.iam.authcore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "directory_link")
@Getter
@Setter
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
}
