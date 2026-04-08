package com.iam.oauth.service;

import com.iam.authcore.entity.AuthCode;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.AuthCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.authcore.repository.TokenRepository;
import com.iam.oauth.dto.TokenRequest;
import com.iam.oauth.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenServiceTest {

    private TokenService service;
    private OAuthClientRepository clientRepo;
    private AuthCodeRepository authCodeRepo;
    private TokenRepository tokenRepo;

    private static final String CLIENT_ID = "client-1";
    private static final String CLIENT_SECRET = "top-secret";
    private static final String CLIENT_SECRET_HASH = hashSecret(CLIENT_SECRET);
    private static final String REDIRECT = "https://app.example.com/callback";

    private static final String TEST_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String TEST_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @BeforeEach
    void setUp() {
        clientRepo = mock(OAuthClientRepository.class);
        authCodeRepo = mock(AuthCodeRepository.class);
        tokenRepo = mock(TokenRepository.class);
        service = new TokenService(clientRepo, authCodeRepo, tokenRepo);
    }

    private static String hashSecret(String secret) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private OAuthClient confidentialClient() {
        OAuthClient c = new OAuthClient();
        c.setClientId(CLIENT_ID);
        c.setClientSecretHash(CLIENT_SECRET_HASH);
        c.setRedirectUris(REDIRECT);
        c.setAllowedScopes("openid profile email");
        c.setIsPublic(false);
        return c;
    }

    private OAuthClient publicClient() {
        OAuthClient c = new OAuthClient();
        c.setClientId(CLIENT_ID);
        c.setClientSecretHash("");
        c.setRedirectUris(REDIRECT);
        c.setAllowedScopes("openid profile");
        c.setIsPublic(true);
        return c;
    }

    private AuthCode validAuthCode() {
        AuthCode ac = new AuthCode();
        ac.setCode("valid-auth-code");
        ac.setClientId(CLIENT_ID);
        ac.setSubject("user-123");
        ac.setCodeChallenge(TEST_CHALLENGE);
        ac.setCodeChallengeMethod("S256");
        ac.setScope("openid");
        ac.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        ac.setConsumedAt(null);
        return ac;
    }

    // --- authorization_code grant tests ---

    @Test
    void authCodeGrant_missingCode_returnsError() {
        TokenRequest req = new TokenRequest("authorization_code", null, REDIRECT, null, CLIENT_ID, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_request", resp.error());
    }

    @Test
    void authCodeGrant_unknownCode_returnsInvalidGrant() {
        when(authCodeRepo.findByCodeAndConsumedAtIsNull("unknown")).thenReturn(Optional.empty());
        TokenRequest req = new TokenRequest("authorization_code", "unknown", REDIRECT, null, CLIENT_ID, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_grant", resp.error());
    }

    @Test
    void authCodeGrant_clientIdMismatch_returnsInvalidGrant() {
        AuthCode ac = validAuthCode();
        ac.setClientId("other-client");
        when(authCodeRepo.findByCodeAndConsumedAtIsNull("valid-auth-code")).thenReturn(Optional.of(ac));
        TokenRequest req = new TokenRequest("authorization_code", "valid-auth-code", REDIRECT, null, CLIENT_ID, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_grant", resp.error());
    }

    @Test
    void authCodeGrant_publicClientMissingPkce_returnsInvalidGrant() {
        AuthCode ac = validAuthCode();
        when(authCodeRepo.findByCodeAndConsumedAtIsNull("valid-auth-code")).thenReturn(Optional.of(ac));
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(publicClient()));
        TokenRequest req = new TokenRequest("authorization_code", "valid-auth-code", REDIRECT, null, CLIENT_ID, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_grant", resp.error());
    }

    @Test
    void authCodeGrant_validRequest_returnsTokens() {
        AuthCode ac = validAuthCode();
        when(authCodeRepo.findByCodeAndConsumedAtIsNull("valid-auth-code")).thenReturn(Optional.of(ac));
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        when(authCodeRepo.save(any(AuthCode.class))).thenAnswer(i -> i.getArgument(0));
        when(tokenRepo.save(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest req = new TokenRequest("authorization_code", "valid-auth-code", REDIRECT, TEST_VERIFIER, CLIENT_ID, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);

        assertNull(resp.error());
        assertNotNull(resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertEquals("Bearer", resp.tokenType());
        assertEquals(3600, resp.expiresIn());
        assertEquals("openid", resp.scope());
    }

    @Test
    void authCodeGrant_consumesAuthCode() {
        AuthCode ac = validAuthCode();
        when(authCodeRepo.findByCodeAndConsumedAtIsNull("valid-auth-code")).thenReturn(Optional.of(ac));
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        when(authCodeRepo.save(any(AuthCode.class))).thenAnswer(i -> i.getArgument(0));
        when(tokenRepo.save(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest req = new TokenRequest("authorization_code", "valid-auth-code", REDIRECT, TEST_VERIFIER, CLIENT_ID, null, null, null);
        service.handleTokenRequest(req);

        verify(authCodeRepo).save(argThat(saved ->
            saved.getConsumedAt() != null
        ));
    }

    // --- client_credentials grant tests ---

    @Test
    void clientCredentials_unknownClient_returnsInvalidClient() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
        TokenRequest req = new TokenRequest("client_credentials", null, null, null, CLIENT_ID, CLIENT_SECRET, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_client", resp.error());
    }

    @Test
    void clientCredentials_wrongSecret_returnsInvalidClient() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        TokenRequest req = new TokenRequest("client_credentials", null, null, null, CLIENT_ID, "wrong-secret", null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_client", resp.error());
    }

    @Test
    void clientCredentials_validClient_returnsAccessTokenOnly() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        when(tokenRepo.save(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest req = new TokenRequest("client_credentials", null, null, null, CLIENT_ID, CLIENT_SECRET, null, null);
        TokenResponse resp = service.handleTokenRequest(req);

        assertNull(resp.error());
        assertNotNull(resp.accessToken());
        assertNull(resp.refreshToken()); // no refresh token for client_credentials
        assertEquals("Bearer", resp.tokenType());
    }

    // --- refresh_token grant tests ---

    @Test
    void refreshGrant_missingToken_returnsError() {
        TokenRequest req = new TokenRequest("refresh_token", null, null, null, null, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_request", resp.error());
    }

    @Test
    void refreshGrant_unknownToken_returnsInvalidGrant() {
        when(tokenRepo.findByJtiAndRevokedFalse("unknown")).thenReturn(Optional.empty());
        TokenRequest req = new TokenRequest("refresh_token", null, null, null, null, null, "unknown", null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("invalid_grant", resp.error());
    }

    @Test
    void refreshGrant_validToken_returnsRotatedTokens() {
        Token oldRefresh = new Token();
        oldRefresh.setJti("old-refresh-jti");
        oldRefresh.setClientId(CLIENT_ID);
        oldRefresh.setSubject("user-123");
        oldRefresh.setScope("openid");
        oldRefresh.setType(Token.TokenType.refresh_token);
        oldRefresh.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        oldRefresh.setRevoked(false);

        when(tokenRepo.findByJtiAndRevokedFalse("old-refresh-jti")).thenReturn(Optional.of(oldRefresh));
        when(tokenRepo.save(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest req = new TokenRequest("refresh_token", null, null, null, null, null, "old-refresh-jti", null);
        TokenResponse resp = service.handleTokenRequest(req);

        assertNull(resp.error());
        assertNotNull(resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertNotEquals("old-refresh-jti", resp.refreshToken()); // rotated
    }

    @Test
    void refreshGrant_revokesOldRefreshToken() {
        Token oldRefresh = new Token();
        oldRefresh.setJti("old-jti");
        oldRefresh.setClientId(CLIENT_ID);
        oldRefresh.setSubject("user-123");
        oldRefresh.setScope("openid");
        oldRefresh.setType(Token.TokenType.refresh_token);
        oldRefresh.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        oldRefresh.setRevoked(false);

        when(tokenRepo.findByJtiAndRevokedFalse("old-jti")).thenReturn(Optional.of(oldRefresh));
        when(tokenRepo.save(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest req = new TokenRequest("refresh_token", null, null, null, null, null, "old-jti", null);
        service.handleTokenRequest(req);

        verify(tokenRepo).save(argThat(token ->
            token.getRevoked() && token.getType() == Token.TokenType.refresh_token
        ));
    }

    // --- unsupported grant type ---

    @Test
    void unsupportedGrantType_returnsError() {
        TokenRequest req = new TokenRequest("unknown_grant", null, null, null, null, null, null, null);
        TokenResponse resp = service.handleTokenRequest(req);
        assertEquals("unsupported_grant_type", resp.error());
    }
}
