package com.iam.oauth.service;

import com.iam.authcore.entity.AuthCode;
import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.repository.AuthCodeRepository;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.oauth.dto.AuthorizationRequest;
import com.iam.oauth.dto.OAuthErrorResponse;
import com.iam.oauth.util.PkceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorization endpoint logic per RFC 6749 §4.1 (Auth Code) and RFC 7636 (PKCE).
 */
@Service
public class AuthorizeService {

    private static final Set<String> SUPPORTED_RESPONSE_TYPES = Set.of("code");
    private static final Set<String> SUPPORTED_PKCE_METHODS = Set.of("S256");
    private static final int AUTH_CODE_TTL_MINUTES = 5;

    private final OAuthClientRepository clientRepo;
    private final AuthCodeRepository authCodeRepo;
    private final SecureRandom random = new SecureRandom();

    public AuthorizeService(OAuthClientRepository clientRepo, AuthCodeRepository authCodeRepo) {
        this.clientRepo = clientRepo;
        this.authCodeRepo = authCodeRepo;
    }

    /**
     * Validate an authorization request.
     *
     * @return an OAuthErrorResponse if validation fails (error must be returned to redirect_uri)
     *         null if validation passes
     */
    public OAuthErrorResponse validateRequest(AuthorizationRequest request) {
        // 1. client_id required
        if (request.clientId() == null || request.clientId().isBlank()) {
            return OAuthErrorResponse.invalidRequest("client_id is required");
        }

        // 2. Client must exist
        OAuthClient client = clientRepo.findByClientId(request.clientId()).orElse(null);
        if (client == null) {
            return OAuthErrorResponse.invalidClient("unknown client_id");
        }

        // 3. redirect_uri required and must match exactly
        if (request.redirectUri() == null || request.redirectUri().isBlank()) {
            return OAuthErrorResponse.invalidRequest("redirect_uri is required");
        }
        Set<String> registeredUris = parseScopes(client.getRedirectUris());
        if (!registeredUris.contains(request.redirectUri())) {
            return OAuthErrorResponse.invalidRequest("redirect_uri mismatch");
        }

        // 4. response_type must be "code"
        if (request.responseType() == null || !SUPPORTED_RESPONSE_TYPES.contains(request.responseType())) {
            return OAuthErrorResponse.unsupportedResponseType("only 'code' response_type is supported");
        }

        // 5. PKCE required for public clients (RFC 7636)
        if (request.requiresPkce()) {
            if (request.codeChallenge() == null || request.codeChallenge().isBlank()) {
                return OAuthErrorResponse.invalidRequest("code_challenge required for public clients");
            }
            if (!SUPPORTED_PKCE_METHODS.contains(request.codeChallengeMethod())) {
                return OAuthErrorResponse.invalidRequest("only S256 code_challenge_method is supported");
            }
            if (!PkceUtils.isValidChallenge(request.codeChallenge())) {
                return OAuthErrorResponse.invalidRequest("invalid code_challenge");
            }
        }

        // 6. Validate scope against registered scopes
        if (request.scope() != null && !request.scope().isBlank()) {
            Set<String> allowedScopes = parseScopes(client.getAllowedScopes());
            for (String requested : request.scopes()) {
                if (!allowedScopes.contains(requested)) {
                    return OAuthErrorResponse.invalidScope("requested scope not allowed for this client");
                }
            }
        }

        return null; // validation passed
    }

    /**
     * Issue a new authorization code.
     *
     * @param request  the validated authorization request
     * @param subject  the authenticated user identifier (set in Phase 5 login)
     * @return the redirect URI with code and state query params
     */
    @Transactional
    public String issueAuthCode(AuthorizationRequest request, String subject) {
        String code = generateCode();

        AuthCode authCode = new AuthCode();
        authCode.setCode(code);
        authCode.setClientId(request.clientId());
        authCode.setSubject(subject);
        authCode.setCodeChallenge(request.codeChallenge() != null ? request.codeChallenge() : "");
        authCode.setCodeChallengeMethod(request.codeChallengeMethod() != null ? request.codeChallengeMethod() : "S256");
        authCode.setScope(request.scope());
        authCode.setNonce(request.state()); // state is echoed back; nonce stored separately in Phase 3 OIDC
        authCode.setExpiresAt(Instant.now().plus(AUTH_CODE_TTL_MINUTES, ChronoUnit.MINUTES));

        authCodeRepo.save(authCode);

        // Build redirect URI with code and state
        StringBuilder uri = new StringBuilder(request.redirectUri())
            .append("?code=").append(code);
        if (request.state() != null) {
            uri.append("&state=").append(request.state());
        }
        return uri.toString();
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Set<String> parseScopes(String scopeStr) {
        if (scopeStr == null || scopeStr.isBlank()) return Set.of();
        // Split on commas and/or whitespace, then filter blanks
        return Arrays.stream(scopeStr.split("[,\\s]+"))
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    }
}
