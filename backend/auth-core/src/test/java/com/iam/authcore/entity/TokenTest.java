package com.iam.authcore.entity;

import com.iam.authcore.AuthCoreTestApplication;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.authcore.repository.TokenRepository;
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
class TokenTest {

    @Autowired
    private TokenRepository repository;

    @Autowired
    private OAuthClientRepository clientRepository;

    @PersistenceContext
    private EntityManager em;

    private OAuthClient persistClient() {
        OAuthClient client = new OAuthClient();
        client.setClientId("token-test-client");
        client.setClientSecretHash("hash");
        client.setRedirectUris("https://example.com/cb");
        client.setAllowedScopes("openid");
        client.setGrantTypes("authorization_code");
        return clientRepository.save(client);
    }

    @Test
    void persistsAccessToken() {
        OAuthClient client = persistClient();

        Token token = new Token();
        token.setJti("jti-access-001");
        token.setType(Token.TokenType.access_token);
        token.setClientId(client.getClientId());
        token.setSubject("user-123");
        token.setScope("openid profile");
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        Token saved = repository.save(token);
        em.flush();

        assertThat(saved.getIssuedAt()).isNotNull();
        assertThat(saved.getRevoked()).isFalse();
    }

    @Test
    void findActiveTokenByJtiReturnsToken() {
        OAuthClient client = persistClient();

        Token token = new Token();
        token.setJti("active-jti");
        token.setType(Token.TokenType.access_token);
        token.setClientId(client.getClientId());
        token.setSubject("user-456");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        repository.save(token);
        em.flush();

        Optional<Token> found = repository.findByJtiAndRevokedFalse("active-jti");

        assertThat(found).isPresent();
        assertThat(found.get().getSubject()).isEqualTo("user-456");
    }

    @Test
    void revokedTokenNotFound() {
        OAuthClient client = persistClient();

        Token token = new Token();
        token.setJti("revoked-jti");
        token.setType(Token.TokenType.access_token);
        token.setClientId(client.getClientId());
        token.setSubject("user-789");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevoked(true);
        repository.save(token);
        em.flush();

        Optional<Token> found = repository.findByJtiAndRevokedFalse("revoked-jti");

        assertThat(found).isEmpty();
    }

    @Test
    void revokeByJtiMarksTokenRevoked() {
        OAuthClient client = persistClient();

        Token token = new Token();
        token.setJti("to-revoke");
        token.setType(Token.TokenType.refresh_token);
        token.setClientId(client.getClientId());
        token.setSubject("user-000");
        token.setExpiresAt(Instant.now().plusSeconds(86400));
        repository.save(token);
        em.flush();

        int updated = repository.revokeByJti("to-revoke");
        em.flush();

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findByJtiAndRevokedFalse("to-revoke")).isEmpty();
    }
}
