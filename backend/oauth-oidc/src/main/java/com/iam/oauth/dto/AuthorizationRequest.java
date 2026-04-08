package com.iam.oauth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Validated authorization request parameters per RFC 6749 §4.3.1.
 */
public record AuthorizationRequest(
    @NotBlank String clientId,
    @NotBlank String redirectUri,
    @NotBlank String responseType,
    String scope,
    String state,
    String codeChallenge,
    String codeChallengeMethod,
    boolean isPublic
) {
    /**
     * Return scopes as an array, empty array if null or blank.
     */
    public String[] scopes() {
        if (scope == null || scope.isBlank()) return new String[0];
        return scope.trim().split("\\s+");
    }

    /**
     * PKCE is required for public clients per RFC 7636.
     */
    public boolean requiresPkce() {
        return isPublic;
    }
}
