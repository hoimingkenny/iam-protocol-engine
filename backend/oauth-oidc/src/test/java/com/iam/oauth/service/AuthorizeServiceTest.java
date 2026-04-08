package com.iam.oauth.service;

import com.iam.authcore.entity.AuthCode;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.repository.AuthCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.oauth.dto.AuthorizationRequest;
import com.iam.oauth.dto.OAuthErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorizeServiceTest {

    private AuthorizeService service;
    private OAuthClientRepository clientRepo;
    private AuthCodeRepository authCodeRepo;

    private static final String REDIRECT = "https://app.example.com/callback";
    private static final String CLIENT_ID = "client-1";

    // RFC 7636 Appendix B test vector
    private static final String TEST_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String TEST_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @BeforeEach
    void setUp() {
        clientRepo = mock(OAuthClientRepository.class);
        authCodeRepo = mock(AuthCodeRepository.class);
        service = new AuthorizeService(clientRepo, authCodeRepo);
    }

    private AuthorizationRequest req(String scope) {
        return new AuthorizationRequest(CLIENT_ID, REDIRECT, "code", scope, "state-1", null, null, false);
    }

    private AuthorizationRequest publicPkceReq(String scope, String challenge, String method) {
        return new AuthorizationRequest(CLIENT_ID, REDIRECT, "code", scope, "state-1", challenge, method, true);
    }

    private OAuthClient publicClient() {
        OAuthClient c = new OAuthClient();
        c.setClientId(CLIENT_ID);
        c.setRedirectUris(REDIRECT);
        c.setAllowedScopes("openid profile");
        c.setIsPublic(true);
        return c;
    }

    private OAuthClient confidentialClient() {
        OAuthClient c = new OAuthClient();
        c.setClientId(CLIENT_ID);
        c.setRedirectUris(REDIRECT);
        c.setAllowedScopes("openid profile email");
        c.setIsPublic(false);
        return c;
    }

    // --- validateRequest tests ---

    @Test
    void validateRequest_missingClientId_returnsInvalidRequest() {
        OAuthErrorResponse err = service.validateRequest(
            new AuthorizationRequest(null, REDIRECT, "code", "openid", "s", null, null, false)
        );
        assertEquals("invalid_request", err.error());
    }

    @Test
    void validateRequest_unknownClient_returnsInvalidClient() {
        when(clientRepo.findByClientId("unknown")).thenReturn(Optional.empty());
        OAuthErrorResponse err = service.validateRequest(
            new AuthorizationRequest("unknown", REDIRECT, "code", "openid", "s", null, null, false)
        );
        assertEquals("invalid_client", err.error());
    }

    @Test
    void validateRequest_redirectUriMismatch_returnsInvalidRequest() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(publicClient()));
        OAuthErrorResponse err = service.validateRequest(
            new AuthorizationRequest(CLIENT_ID, "https://evil.com/callback", "code", "openid", "s", null, null, true)
        );
        assertEquals("invalid_request", err.error());
    }

    @Test
    void validateRequest_unsupportedResponseType_returnsUnsupportedResponseType() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        OAuthErrorResponse err = service.validateRequest(
            new AuthorizationRequest(CLIENT_ID, REDIRECT, "token", "openid", "s", null, null, false)
        );
        assertEquals("unsupported_response_type", err.error());
    }

    @Test
    void validateRequest_publicClientMissingPkce_returnsInvalidRequest() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(publicClient()));
        OAuthErrorResponse err = service.validateRequest(
            new AuthorizationRequest(CLIENT_ID, REDIRECT, "code", "openid", "s", null, null, true)
        );
        assertEquals("invalid_request", err.error()); // code_challenge required
    }

    @Test
    void validateRequest_publicClientValidPkce_returnsNull() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(publicClient()));
        OAuthErrorResponse err = service.validateRequest(
            publicPkceReq("openid", TEST_CHALLENGE, "S256")
        );
        assertNull(err);
    }

    @Test
    void validateRequest_scopeNotAllowed_returnsInvalidScope() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        OAuthErrorResponse err = service.validateRequest(req("admin"));
        assertEquals("invalid_scope", err.error());
    }

    @Test
    void validateRequest_validConfidentialClient_returnsNull() {
        when(clientRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(confidentialClient()));
        OAuthErrorResponse err = service.validateRequest(req("openid"));
        assertNull(err);
    }

    // --- issueAuthCode tests ---

    @Test
    void issueAuthCode_returnsRedirectWithCodeAndState() {
        when(authCodeRepo.save(any(AuthCode.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizationRequest request = req("openid");
        String redirect = service.issueAuthCode(request, "user-123");

        assertTrue(redirect.startsWith(REDIRECT + "?code="));
        assertTrue(redirect.contains("&state=state-1"));
    }

    @Test
    void issueAuthCode_withPkce_storesChallengeAndMethod() {
        when(authCodeRepo.save(any(AuthCode.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizationRequest request = publicPkceReq("openid", TEST_CHALLENGE, "S256");
        service.issueAuthCode(request, "user-123");

        verify(authCodeRepo).save(argThat(authCode ->
            TEST_CHALLENGE.equals(authCode.getCodeChallenge()) &&
            "S256".equals(authCode.getCodeChallengeMethod()) &&
            "user-123".equals(authCode.getSubject())
        ));
    }

    @Test
    void issueAuthCode_setsExpiryTo5Minutes() {
        when(authCodeRepo.save(any(AuthCode.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizationRequest request = req("openid");
        var before = java.time.Instant.now();

        service.issueAuthCode(request, "user-123");

        verify(authCodeRepo).save(argThat(authCode ->
            authCode.getExpiresAt() != null &&
            authCode.getExpiresAt().isAfter(before.plusSeconds(4 * 60)) &&
            authCode.getExpiresAt().isBefore(before.plusSeconds(6 * 60 + 1))
        ));
    }
}
