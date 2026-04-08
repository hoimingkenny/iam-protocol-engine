package com.iam.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 6749 §4.1.2.1 error response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthErrorResponse(
    String error,
    String errorDescription,
    String errorUri,
    String state
) {
    public static OAuthErrorResponse invalidRequest(String description) {
        return new OAuthErrorResponse("invalid_request", description, null, null);
    }

    public static OAuthErrorResponse invalidClient(String description) {
        return new OAuthErrorResponse("invalid_client", description, null, null);
    }

    public static OAuthErrorResponse unauthorizedClient(String description) {
        return new OAuthErrorResponse("unauthorized_client", description, null, null);
    }

    public static OAuthErrorResponse unsupportedResponseType(String description) {
        return new OAuthErrorResponse("unsupported_response_type", description, null, null);
    }

    public static OAuthErrorResponse invalidScope(String description) {
        return new OAuthErrorResponse("invalid_scope", description, null, null);
    }

    public static OAuthErrorResponse serverError(String description) {
        return new OAuthErrorResponse("server_error", description, null, null);
    }

    public OAuthErrorResponse withState(String state) {
        return new OAuthErrorResponse(this.error, this.errorDescription, this.errorUri, state);
    }
}
