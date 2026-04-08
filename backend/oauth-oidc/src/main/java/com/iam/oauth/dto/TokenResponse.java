package com.iam.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Token response per RFC 6749 §5.1 and RFC 6749 §5.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
    String accessToken,
    String tokenType,
    Integer expiresIn,
    String refreshToken,
    String idToken,
    String scope,
    String error,
    String errorDescription
) {
    public static TokenResponse success(String accessToken, String refreshToken, int expiresIn, String scope) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken, null, scope, null, null);
    }

    public static TokenResponse accessTokenOnly(String accessToken, int expiresIn, String scope) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, null, null, scope, null, null);
    }

    public static TokenResponse error(String error, String description) {
        return new TokenResponse(null, null, null, null, null, null, error, description);
    }
}
