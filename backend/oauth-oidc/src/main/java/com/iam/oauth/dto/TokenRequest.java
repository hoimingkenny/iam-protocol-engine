package com.iam.oauth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Token request per RFC 6749 §3.2.
 * Handles authorization_code, client_credentials, and refresh_token grants.
 */
public record TokenRequest(
    @NotBlank String grantType,

    // authorization_code grant
    String code,
    String redirectUri,
    String codeVerifier,

    // client_credentials grant
    String clientId,
    String clientSecret,

    // refresh_token grant
    String refreshToken,

    // shared
    String scope
) {
    public boolean isAuthorizationCodeGrant() {
        return "authorization_code".equalsIgnoreCase(grantType);
    }

    public boolean isClientCredentialsGrant() {
        return "client_credentials".equalsIgnoreCase(grantType);
    }

    public boolean isRefreshTokenGrant() {
        return "refresh_token".equalsIgnoreCase(grantType);
    }
}
