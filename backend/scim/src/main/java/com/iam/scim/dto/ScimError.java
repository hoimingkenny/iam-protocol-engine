package com.iam.scim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SCIM 2.0 Error response per RFC 7644 §3.12.
 */
public record ScimError(
    @JsonProperty("scimType") String scimType,
    @JsonProperty("detail") String detail,
    @JsonProperty("status") int status
) {
    public static ScimError of(int status, String detail) {
        return new ScimError(null, detail, status);
    }

    public static ScimError badRequest(String detail) {
        return of(400, detail);
    }

    public static ScimError notFound(String detail) {
        return of(404, detail);
    }

    public static ScimError conflict(String detail) {
        return of(409, detail);
    }
}
