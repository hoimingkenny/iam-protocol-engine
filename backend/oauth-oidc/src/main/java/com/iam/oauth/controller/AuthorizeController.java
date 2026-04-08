package com.iam.oauth.controller;

import com.iam.oauth.dto.AuthorizationRequest;
import com.iam.oauth.dto.OAuthErrorResponse;
import com.iam.oauth.service.AuthorizeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * OAuth 2.0 Authorization Endpoint.
 *
 * GET /authorize handles authorization requests.
 * For Phase 2 the user is pre-authenticated (subject param).
 * Phase 5 will integrate a real login/consent page.
 *
 * Error responses: RFC 6749 §4.1.2 — errors are redirect to redirect_uri with query params.
 * Only server_error or invalid_client (without redirect_uri) returns 4xx.
 */
@RestController
@RequestMapping("/oauth2")
public class AuthorizeController {

    private final AuthorizeService authorizeService;

    public AuthorizeController(AuthorizeService authorizeService) {
        this.authorizeService = authorizeService;
    }

    /**
     * Authorization endpoint per RFC 6749 §4.1.1.
     *
     * @param clientId         Required. Client identifier.
     * @param redirectUri      Required. Must exactly match registered URI.
     * @param responseType     Required. Must be "code".
     * @param scope            Optional. Space-separated scopes.
     * @param state            Optional. Opaque state value echoed back.
     * @param codeChallenge    Optional (required for public clients). PKCE challenge.
     * @param codeChallengeMethod Optional. "S256" (only supported method).
     * @param subject          Temporary: pre-authenticated user ID (Phase 5: replaced by session).
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
            @RequestParam(value = "subject", required = false) String subject
    ) {
        // Determine if client is public (Phase 2: query from DB; default to public=true for safety)
        // This will be looked up properly when we build the full service
        AuthorizationRequest request = buildRequest(clientId, redirectUri, responseType,
                scope, state, codeChallenge, codeChallengeMethod);

        OAuthErrorResponse error = authorizeService.validateRequest(request);
        if (error != null) {
            return errorToRedirect(error, redirectUri);
        }

        // subject is required; in Phase 5 this comes from the login session
        if (subject == null || subject.isBlank()) {
            OAuthErrorResponse err = OAuthErrorResponse.invalidRequest(
                "subject (user) is required — login not yet integrated (Phase 5)");
            return errorToRedirect(err, redirectUri);
        }

        String redirectUrl = authorizeService.issueAuthCode(request, subject);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build();
    }

    private AuthorizationRequest buildRequest(String clientId, String redirectUri,
            String responseType, String scope, String state,
            String codeChallenge, String codeChallengeMethod) {
        // Phase 2: assume public=true (PKCE required). In Phase 5 this is loaded from the DB client record.
        return new AuthorizationRequest(
            clientId, redirectUri, responseType, scope, state,
            codeChallenge, codeChallengeMethod, true
        );
    }

    /**
     * Redirect with RFC 6749 error params.
     */
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
