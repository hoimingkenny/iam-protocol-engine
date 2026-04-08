package com.iam.authcore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "auth_code")
@Getter
@Setter
public class AuthCode {

    @Id
    @Column(name = "code", length = 128)
    private String code;

    @Column(name = "client_id", length = 128, nullable = false)
    private String clientId;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "code_challenge", length = 128, nullable = false)
    private String codeChallenge;

    @Column(name = "code_challenge_method", length = 16, nullable = false)
    private String codeChallengeMethod = "S256";

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "nonce", length = 256)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
