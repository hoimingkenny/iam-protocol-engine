package com.iam.oauth.service;

import com.iam.authcore.entity.AuthCode;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.AuthCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.authcore.repository.TokenRepository;
import com.iam.oauth.dto.TokenRequest;
import com.iam.oauth.dto.TokenResponse;
import com.iam.oauth.security.IdTokenGenerator;
import com.iam.oauth.util.PkceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import static com.iam.authcore.entity.Token.TokenType;

/**
 * Token endpoint logic per RFC 6749 §3.2 and RFC 7636 §4.6.
 *
 * Phase 2: issues opaque bearer tokens (random strings) stored in DB.
 * Phase 3: replaces access tokens with RS256-signed JWTs.
 */
@Service
public class TokenService {

    private static final int ACCESS_TOKEN_TTL_SECONDS = 3600;       // 1 hour
    private static final int REFRESH_TOKEN_TTL_DAYS = 7;

    private final OAuthClientRepository clientRepo;
    private final AuthCodeRepository authCodeRepo;
    private final TokenRepository tokenRepo;
    private final IdTokenGenerator idTokenGenerator;
    private final SecureRandom random = new SecureRandom();

    public TokenService(OAuthClientRepository clientRepo,
                        AuthCodeRepository authCodeRepo,
                        TokenRepository tokenRepo,
                        IdTokenGenerator idTokenGenerator) {
        this.clientRepo = clientRepo;
        this.authCodeRepo = authCodeRepo;
        this.tokenRepo = tokenRepo;
        this.idTokenGenerator = idTokenGenerator;
    }

    /**
     * Handle any grant type and return either a success response or an error response.
     * Callers should check for error() != null on the result.
     */
    @Transactional
    public TokenResponse handleTokenRequest(TokenRequest request) {
        if (request.isAuthorizationCodeGrant()) {
            return handleAuthorizationCodeGrant(request);
        } else if (request.isClientCredentialsGrant()) {
            return handleClientCredentialsGrant(request);
        } else if (request.isRefreshTokenGrant()) {
            return handleRefreshTokenGrant(request);
        } else {
            return TokenResponse.error("unsupported_grant_type", "unsupported grant_type");
        }
    }

    // --- Authorization Code Grant (RFC 6749 §4.1) ---

    private TokenResponse handleAuthorizationCodeGrant(TokenRequest request) {
        // 1. code required
        if (request.code() == null || request.code().isBlank()) {
            return TokenResponse.error("invalid_request", "code is required");
        }

        // 2. Find auth code — must exist and not be consumed
        Optional<AuthCode> optCode = authCodeRepo.findByCodeAndConsumedAtIsNull(request.code());
        if (optCode.isEmpty()) {
            return TokenResponse.error("invalid_grant", "authorization code is invalid or expired");
        }
        AuthCode authCode = optCode.get();

        // 3. Client must match
        if (!authCode.getClientId().equals(request.clientId())) {
            return TokenResponse.error("invalid_grant", "client_id mismatch");
        }

        // 4. PKCE verification (RFC 7636 §4.6) — required for public clients
        if (!verifyPkce(authCode, request.codeVerifier(), request.clientId())) {
            return TokenResponse.error("invalid_grant", "code_verifier verification failed");
        }

        // 5. Consume the auth code
        authCode.setConsumedAt(Instant.now());
        authCodeRepo.save(authCode);

        // 6. Determine scopes (use requested scope if provided, otherwise use auth code scope)
        String scope = resolveScope(request.scope(), authCode.getScope());

        // 7. Issue tokens with a new family_id
        String subject = authCode.getSubject() != null ? authCode.getSubject() : "";
        String familyId = generateId(); // new family per auth code exchange
        Token accessToken = createAccessToken(authCode.getClientId(), subject, scope, familyId);
        Token refreshToken = createRefreshToken(authCode.getClientId(), subject, scope, familyId);

        tokenRepo.save(accessToken);
        tokenRepo.save(refreshToken);

        // 8. Issue ID token (OIDC Core 1.0 §3.1.3.3)
        String idToken = idTokenGenerator.generateIdToken(
            subject, authCode.getClientId(), authCode.getNonce());

        return new TokenResponse(
            accessToken.getJti(),
            "Bearer",
            ACCESS_TOKEN_TTL_SECONDS,
            refreshToken.getJti(),
            idToken,
            scope,
            null,
            null
        );
    }

    // --- Client Credentials Grant (RFC 6749 §4.2) ---

    private TokenResponse handleClientCredentialsGrant(TokenRequest request) {
        // 1. Validate client credentials
        OAuthClient client = validateClientCredentials(request.clientId(), request.clientSecret());
        if (client == null) {
            return TokenResponse.error("invalid_client", "client authentication failed");
        }

        // 2. Issue access token (no refresh token for client_credentials)
        String scope = resolveScope(request.scope(), client.getAllowedScopes());
        Token accessToken = createAccessTokenForClient(client.getClientId(), scope);

        tokenRepo.save(accessToken);

        return TokenResponse.accessTokenOnly(
            accessToken.getJti(),
            ACCESS_TOKEN_TTL_SECONDS,
            scope
        );
    }

    /**
     * Issues tokens for a SAML-authenticated user.
     * Called by the SAML → OIDC bridge after successful assertion validation.
     *
     * @param subject the SAML NameID (used as the OAuth subject / sub claim)
     * @param clientId the OAuth client ID (from RelayState)
     * @param nonce optional nonce for ID token (may be null)
     * @param scope requested scopes (uses client default scopes if null/blank)
     * @return TokenResponse with access_token, refresh_token, id_token
     */
    public TokenResponse issueTokensForSamlUser(String subject, String clientId,
                                                 String nonce, String scope) {
        // Validate client exists
        Optional<OAuthClient> optClient = clientRepo.findByClientId(clientId);
        if (optClient.isEmpty()) {
            return TokenResponse.error("invalid_client", "client not found");
        }
        OAuthClient client = optClient.get();

        // Resolve scope
        String resolvedScope = resolveScope(scope, client.getAllowedScopes());

        // Issue tokens
        String familyId = generateId();
        Token accessToken = createAccessToken(clientId, subject, resolvedScope, familyId);
        Token refreshToken = createRefreshToken(clientId, subject, resolvedScope, familyId);

        tokenRepo.save(accessToken);
        tokenRepo.save(refreshToken);

        // Issue ID token
        String idToken = idTokenGenerator.generateIdToken(subject, clientId, nonce);

        return new TokenResponse(
            accessToken.getJti(),
            "Bearer",
            ACCESS_TOKEN_TTL_SECONDS,
            refreshToken.getJti(),
            idToken,
            resolvedScope,
            null,
            null
        );
    }

    // --- Refresh Token Grant (RFC 6749 §6) ---

    private TokenResponse handleRefreshTokenGrant(TokenRequest request) {
        // 1. Refresh token required
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            return TokenResponse.error("invalid_request", "refresh_token is required");
        }

        // 2. Find and validate refresh token
        Optional<Token> optToken = tokenRepo.findByJtiAndRevokedFalse(request.refreshToken());
        if (optToken.isEmpty()) {
            return TokenResponse.error("invalid_grant", "refresh token is invalid or expired");
        }
        Token oldRefresh = optToken.get();

        if (oldRefresh.getType() != TokenType.refresh_token) {
            return TokenResponse.error("invalid_grant", "not a refresh token");
        }

        // 3. Determine scope
        String scope = resolveScope(request.scope(), oldRefresh.getScope());

        // 4. Rotate: revoke all tokens in the family (including old access token)
        //    This prevents token family extension attacks (RFC 6749 §6 threat model).
        String familyId = oldRefresh.getFamilyId();
        if (familyId != null) {
            tokenRepo.revokeAllByFamilyId(familyId);
        } else {
            // Pre-Phase-4 tokens have no family — revoke just the refresh token
            oldRefresh.setRevoked(true);
            tokenRepo.save(oldRefresh);
        }

        // 5. Issue new access token + new refresh token in the same family
        String newFamilyId = familyId != null ? familyId : generateId();
        Token newAccess = createAccessToken(oldRefresh.getClientId(), oldRefresh.getSubject(), scope, newFamilyId);
        Token newRefresh = createRefreshToken(oldRefresh.getClientId(), oldRefresh.getSubject(), scope, newFamilyId);

        tokenRepo.save(newAccess);
        tokenRepo.save(newRefresh);

        return TokenResponse.success(
            newAccess.getJti(),
            newRefresh.getJti(),
            ACCESS_TOKEN_TTL_SECONDS,
            scope
        );
    }

    // --- PKCE verification ---

    private boolean verifyPkce(AuthCode authCode, String codeVerifier, String clientId) {
        Optional<OAuthClient> optClient = clientRepo.findByClientId(clientId);
        if (optClient.isEmpty()) return false;

        OAuthClient client = optClient.get();
        boolean pkceRequired = client.getIsPublic();

        // Missing verifier is only acceptable when PKCE is optional
        if (pkceRequired && (codeVerifier == null || codeVerifier.isBlank())) return false;
        if (codeVerifier == null || codeVerifier.isBlank()) return true;

        return PkceUtils.verifyCodeChallenge(codeVerifier,
            authCode.getCodeChallenge(), authCode.getCodeChallengeMethod());
    }

    // --- Token creation helpers ---

    private Token createAccessToken(String clientId, String subject, String scope, String familyId) {
        return createToken(clientId, subject, scope, TokenType.access_token,
            Instant.now().plus(ACCESS_TOKEN_TTL_SECONDS, ChronoUnit.SECONDS), familyId);
    }

    private Token createAccessTokenForClient(String clientId, String scope) {
        return createToken(clientId, "", scope, TokenType.access_token,
            Instant.now().plus(ACCESS_TOKEN_TTL_SECONDS, ChronoUnit.SECONDS), null);
    }

    private Token createRefreshToken(String clientId, String subject, String scope, String familyId) {
        return createToken(clientId, subject, scope, TokenType.refresh_token,
            Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS), familyId);
    }

    private Token createToken(String clientId, String subject, String scope,
                               TokenType type, Instant expiresAt, String familyId) {
        Token token = new Token();
        token.setJti(generateId());
        token.setClientId(clientId);
        token.setSubject(subject);
        token.setScope(scope);
        token.setType(type);
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        token.setFamilyId(familyId);
        return token;
    }

    private String generateId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // --- Client credential validation ---

    private OAuthClient validateClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) return null;
        Optional<OAuthClient> optClient = clientRepo.findByClientId(clientId);
        if (optClient.isEmpty()) return null;
        OAuthClient client = optClient.get();

        // For public clients, secret is not required (PKCE takes its place)
        if (client.getIsPublic()) return client;

        // For confidential clients, verify secret using SHA-256 hash
        String hash = hashSecret(clientSecret);
        if (!hash.equals(client.getClientSecretHash())) return null;

        return client;
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Resolve the effective scope: if requested scope is present and valid, use it;
     * otherwise fall back to the pre-authorized scope.
     */
    private String resolveScope(String requested, String authorized) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return authorized != null ? authorized : "";
    }
}
