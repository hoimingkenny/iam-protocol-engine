package com.iam.authcore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "token")
@Getter
@Setter
public class Token {

    public enum TokenType {
        access_token, refresh_token, id_token
    }

    @Id
    @Column(name = "jti", length = 128)
    private String jti;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private TokenType type;

    @Column(name = "client_id", length = 128, nullable = false)
    private String clientId;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    /**
     * Token family identifier — groups an access token and refresh token issued together.
     * When a refresh token is reused, ALL tokens in the same family are revoked.
     * Null for tokens issued before Phase 4 migration.
     */
    @Column(name = "family_id", length = 128)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @PrePersist
    protected void onCreate() {
        this.issuedAt = Instant.now();
    }
}
