package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_code")
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getCodeChallenge() { return codeChallenge; }
    public void setCodeChallenge(String challenge) { this.codeChallenge = challenge; }
    public String getCodeChallengeMethod() { return codeChallengeMethod; }
    public void setCodeChallengeMethod(String method) { this.codeChallengeMethod = method; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
