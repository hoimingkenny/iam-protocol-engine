package com.iam.scim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * SCIM 2.0 ListResponse per RFC 7644 §3.4.2.2.
 */
public record ScimListResponse<T>(
    @JsonProperty("totalResults") int totalResults,
    @JsonProperty("startIndex") int startIndex,
    @JsonProperty("itemsPerPage") int itemsPerPage,
    @JsonProperty("Resources") List<T> resources
) {
    public static <T> ScimListResponse<T> of(List<T> items, int startIndex) {
        return new ScimListResponse<>(items.size(), startIndex, items.size(), items);
    }
}
