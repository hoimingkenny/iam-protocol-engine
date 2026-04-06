package com.iam.authcore.entity;

import com.iam.authcore.AuthCoreTestApplication;
import com.iam.authcore.repository.OAuthClientRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthCoreTestApplication.class)
@ActiveProfiles("test")
@Transactional
class OAuthClientTest {

    @Autowired
    private OAuthClientRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void persistsOAuthClientWithAllFields() {
        OAuthClient client = new OAuthClient();
        client.setClientId("test-client");
        client.setClientSecretHash("$2a$10$hashedsecret");
        client.setClientName("Test Client");
        client.setRedirectUris("https://app.example.com/cb,https://staging.example.com/cb");
        client.setAllowedScopes("openid,profile,api:read");
        client.setGrantTypes("authorization_code,client_credentials");
        client.setIsPublic(false);

        OAuthClient saved = repository.save(client);
        em.flush();
        em.clear();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByClientIdReturnsClient() {
        OAuthClient client = new OAuthClient();
        client.setClientId("find-me");
        client.setClientSecretHash("hash");
        client.setRedirectUris("https://example.com/cb");
        client.setAllowedScopes("openid");
        client.setGrantTypes("authorization_code");
        repository.save(client);

        Optional<OAuthClient> found = repository.findByClientId("find-me");

        assertThat(found).isPresent();
        assertThat(found.get().getClientName()).isNull();
    }

    @Test
    void updatedAtChangesOnUpdate() {
        OAuthClient client = new OAuthClient();
        client.setClientId("updatable-client");
        client.setClientSecretHash("hash");
        client.setRedirectUris("https://example.com/cb");
        client.setAllowedScopes("openid");
        client.setGrantTypes("authorization_code");
        repository.save(client);

        em.flush();
        Instant beforeUpdate = repository.findByClientId("updatable-client").get().getUpdatedAt();

        client.setClientName("Updated Name");
        repository.save(client);
        em.flush();

        OAuthClient after = repository.findByClientId("updatable-client").get();
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }
}
