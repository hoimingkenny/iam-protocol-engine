package com.iam.authcore.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "oauth_client")
public class OAuthClient {

    @Id
    @Column(name = "client_id", length = 128)
    private String clientId;

    @Column(name = "client_secret_hash", length = 256, nullable = false)
    private String clientSecretHash;

    @Column(name = "client_name", length = 256)
    private String clientName;

    @Column(name = "redirect_uris", columnDefinition = "TEXT", nullable = false)
    private String redirectUris;

    @Column(name = "allowed_scopes", columnDefinition = "TEXT", nullable = false)
    private String allowedScopes;

    @Column(name = "grant_types", columnDefinition = "TEXT", nullable = false)
    private String grantTypes;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

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

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public void setClientSecretHash(String hash) { this.clientSecretHash = hash; }
    public String getClientName() { return clientName; }
    public void setClientName(String name) { this.clientName = name; }
    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String uris) { this.redirectUris = uris; }
    public String getAllowedScopes() { return allowedScopes; }
    public void setAllowedScopes(String scopes) { this.allowedScopes = scopes; }
    public String getGrantTypes() { return grantTypes; }
    public void setGrantTypes(String grants) { this.grantTypes = grants; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
