package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "token")
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

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }
    public TokenType getType() { return type; }
    public void setType(TokenType type) { this.type = type; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public Instant getIssuedAt() { return issuedAt; }
}
