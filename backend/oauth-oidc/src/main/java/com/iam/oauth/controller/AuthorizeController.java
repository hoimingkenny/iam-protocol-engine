package com.iam.oauth.controller;

import com.iam.authcore.entity.OAuthClient;
import com.iam.authcore.repository.OAuthClientRepository;
import com.iam.oauth.dto.AuthorizationRequest;
import com.iam.oauth.dto.OAuthErrorResponse;
import com.iam.oauth.service.AuthorizeService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * OAuth 2.0 Authorization Endpoint.
 *
 * GET /authorize handles authorization requests.
 * Phase 2: user is pre-authenticated via subject param.
 * Phase 5: user authenticates via login_token (from POST /login).
 *
 * Error responses: RFC 6749 §4.1.2 — errors are redirected to redirect_uri with query params.
 */
@RestController
@RequestMapping("/oauth2")
public class AuthorizeController {

    private static final String LOGIN_TOKEN_PREFIX = "login:";

    private final AuthorizeService authorizeService;
    private final OAuthClientRepository clientRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    public AuthorizeController(AuthorizeService authorizeService,
                              OAuthClientRepository clientRepo,
                              RedisTemplate<String, Object> redisTemplate) {
        this.authorizeService = authorizeService;
        this.clientRepo = clientRepo;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Authorization endpoint per RFC 6749 §4.1.1.
     *
     * Phase 2: subject passed directly (pre-authenticated, for testing).
     * Phase 5: subject obtained from login_token (Redis lookup).
     *
     * @param clientId             Required. Client identifier.
     * @param redirectUri           Required. Must exactly match registered URI.
     * @param responseType         Required. Must be "code".
     * @param scope                Optional. Space-separated scopes.
     * @param state                Optional. Opaque state value echoed back.
     * @param codeChallenge        Optional (required for public clients). PKCE challenge.
     * @param codeChallengeMethod  Optional. "S256" (only supported method).
     * @param subject              Phase 2 only: pre-authenticated user ID (deprecated).
     * @param loginToken           Phase 5: login token from POST /login.
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("response_type") String responseType,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "login_token", required = false) String loginToken
    ) {
        // Look up client to determine isPublic (PKCE requirement)
        OAuthClient client = clientRepo.findByClientId(clientId).orElse(null);
        if (client == null) {
            OAuthErrorResponse err = OAuthErrorResponse.invalidClient("unknown client_id");
            return errorToRedirect(err, redirectUri);
        }

        AuthorizationRequest request = new AuthorizationRequest(
            clientId, redirectUri, responseType, scope, state,
            codeChallenge, codeChallengeMethod, client.getIsPublic()
        );

        OAuthErrorResponse validationError = authorizeService.validateRequest(request);
        if (validationError != null) {
            return errorToRedirect(validationError, redirectUri);
        }

        // Resolve authenticated subject: prefer login_token (Phase 5), fall back to subject (Phase 2)
        String resolvedSubject = resolveSubject(subject, loginToken);
        if (resolvedSubject == null) {
            // Redirect to the Admin UI login page
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("http://localhost:5173/login?redirect_uri=" + redirectUri))
                .build();
        }

        String redirectUrl = authorizeService.issueAuthCode(request, resolvedSubject);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build();
    }

    /**
     * Resolve the authenticated subject from either a Phase 5 login token or Phase 2 subject param.
     *
     * @return the subject, or null if neither is present
     */
    private String resolveSubject(String subject, String loginToken) {
        // Phase 5: login_token from /login endpoint (Redis lookup)
        if (loginToken != null && !loginToken.isBlank()) {
            Object stored = redisTemplate.opsForValue().get(LOGIN_TOKEN_PREFIX + loginToken);
            if (stored != null) {
                return stored.toString();
            }
            return null; // invalid/expired login token
        }
        // Phase 2: subject passed directly (for testing with pre-authenticated sessions)
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return null;
    }

    private ResponseEntity<Void> errorToRedirect(OAuthErrorResponse error, String redirectUri) {
        StringBuilder url = new StringBuilder(redirectUri)
            .append("?error=").append(error.error())
            .append("&error_description=").append(error.errorDescription() != null
                ? error.errorDescription().replace(" ", "+") : "");
        if (error.state() != null) {
            url.append("&state=").append(error.state());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(url.toString()))
            .build();
    }
}
