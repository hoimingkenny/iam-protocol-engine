package com.iam.scim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SCIM 2.0 User resource per RFC 7643 §5.1.
 */
public record ScimUserDto(
    @JsonProperty("id") String id,
    @JsonProperty("userName") String userName,
    @JsonProperty("name") NameDto name,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("emails") List<EmailDto> emails,
    @JsonProperty("active") Boolean active,
    @JsonProperty("groups") List<GroupMembership> groups,
    @JsonProperty("meta") MetaDto meta,
    @JsonProperty("externalId") String externalId,
    @JsonProperty("attributes") Map<String, Object> attributes
) {
    public record NameDto(
        @JsonProperty("formatted") String formatted,
        @JsonProperty("familyName") String familyName,
        @JsonProperty("givenName") String givenName
    ) {}

    public record EmailDto(
        @JsonProperty("value") String value,
        @JsonProperty("type") String type,
        @JsonProperty("primary") Boolean primary
    ) {}

    public record GroupMembership(
        @JsonProperty("value") String value,
        @JsonProperty("$ref") String ref,
        @JsonProperty("display") String display
    ) {}

    public record MetaDto(
        @JsonProperty("resourceType") String resourceType,
        @JsonProperty("created") Instant created,
        @JsonProperty("lastModified") Instant lastModified,
        @JsonProperty("location") String location,
        @JsonProperty("version") String version
    ) {}

    public static ScimUserDto from(
            UUID id,
            String userName,
            String displayName,
            String emails,
            Boolean active,
            String groups,
            String externalId,
            Map<String, Object> attributes,
            Instant createdAt,
            Instant updatedAt,
            String location
    ) {
        return new ScimUserDto(
            id.toString(),
            userName,
            new NameDto(displayName, null, null),
            displayName,
            parseEmails(emails),
            active,
            parseGroupMemberships(groups),
            new MetaDto("User", createdAt, updatedAt, location, null),
            externalId,
            attributes
        );
    }

    private static List<EmailDto> parseEmails(String emails) {
        if (emails == null || emails.isBlank()) return List.of();
        return List.of(new EmailDto(emails.split(",")[0].trim(), "work", true));
    }

    private static List<GroupMembership> parseGroupMemberships(String groups) {
        if (groups == null || groups.isBlank()) return List.of();
        return List.of();
    }
}
