package com.iam.scim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SCIM 2.0 Group resource per RFC 7643 §5.2.
 */
public record ScimGroupDto(
    @JsonProperty("id") String id,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("members") List<MemberDto> members,
    @JsonProperty("meta") ScimUserDto.MetaDto meta,
    @JsonProperty("externalId") String externalId,
    @JsonProperty("attributes") Map<String, Object> attributes
) {
    public record MemberDto(
        @JsonProperty("value") String value,
        @JsonProperty("$ref") String ref,
        @JsonProperty("type") String type
    ) {}

    public static ScimGroupDto from(
            UUID id,
            String displayName,
            String members,
            String externalId,
            Map<String, Object> attributes,
            Instant createdAt,
            Instant updatedAt,
            String location
    ) {
        return new ScimGroupDto(
            id.toString(),
            displayName,
            parseMembers(members),
            new ScimUserDto.MetaDto("Group", createdAt, updatedAt, location, null),
            externalId,
            attributes
        );
    }

    private static List<MemberDto> parseMembers(String memberIds) {
        // members field stores comma-separated ScimUser UUIDs
        if (memberIds == null || memberIds.isBlank()) return List.of();
        List<MemberDto> result = new java.util.ArrayList<>();
        for (String mid : memberIds.split(",")) {
            String trimmed = mid.trim();
            if (!trimmed.isBlank()) {
                result.add(new MemberDto(trimmed, null, "User"));
            }
        }
        return result;
    }
}
