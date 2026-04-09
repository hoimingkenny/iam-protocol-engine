package com.iam.scim.service;

import com.iam.authcore.entity.ScimGroup;
import com.iam.authcore.entity.ScimUser;
import com.iam.scim.dto.ScimError;
import com.iam.scim.dto.ScimGroupDto;
import com.iam.scim.dto.ScimListResponse;
import com.iam.scim.repository.ScimGroupRepository;
import com.iam.scim.repository.ScimUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * SCIM 2.0 Group management per RFC 7644 §5.3.
 *
 * Supports: POST, GET, PATCH (member modify), DELETE on /scim/v2/Groups
 */
@Service
public class ScimGroupService {

    private static final String CONTENT_TYPE = "application/scim+json";
    private static final String BASE_LOCATION = "http://localhost:8080/scim/v2/Groups";

    private final ScimGroupRepository groupRepo;
    private final ScimUserRepository userRepo;

    public ScimGroupService(ScimGroupRepository groupRepo, ScimUserRepository userRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    public String getContentType() { return CONTENT_TYPE; }

    /**
     * Create a new SCIM group.
     * POST /scim/v2/Groups
     */
    @Transactional
    public Object createGroup(ScimGroupDto dto) {
        if (dto.displayName() == null || dto.displayName().isBlank()) {
            return ScimError.badRequest("displayName is required");
        }

        ScimGroup group = new ScimGroup();
        group.setDisplayName(dto.displayName());
        group.setExternalId(dto.externalId());
        group.setAttributes(dto.attributes());
        // Members will be set separately via PATCH
        group.setMembers("");

        ScimGroup saved = groupRepo.save(group);
        String location = BASE_LOCATION + "/" + saved.getId();

        return new CreateResult(saved, location, 201);
    }

    /**
     * List SCIM groups with optional filtering.
     * GET /scim/v2/Groups?filter=displayName eq "..."&startIndex=1&count=10
     */
    public Object listGroups(String filter, int startIndex, int count) {
        Pageable pageable = PageRequest.of(
            Math.max(0, (startIndex - 1) / Math.max(count, 1)),
            count
        );

        Page<ScimGroup> page;
        if (filter != null && filter.contains("displayName")) {
            String value = extractFilterValue(filter);
            page = groupRepo.findByDisplayNameContaining(value, pageable);
        } else {
            page = groupRepo.findAll(pageable);
        }

        List<ScimGroupDto> dtos = page.getContent().stream()
            .map(g -> toDto(g, BASE_LOCATION + "/" + g.getId()))
            .toList();

        return ScimListResponse.of(dtos, startIndex);
    }

    /**
     * Get a single SCIM group by ID.
     * GET /scim/v2/Groups/{id}
     */
    public Object getGroup(UUID id) {
        Optional<ScimGroup> opt = groupRepo.findById(id);
        if (opt.isEmpty()) {
            return ScimError.notFound("Group not found: " + id);
        }
        return toDto(opt.get(), BASE_LOCATION + "/" + opt.get().getId());
    }

    /**
     * Patch (modify) a SCIM group — specifically add/remove members.
     * PATCH /scim/v2/Groups/{id}
     * RFC 7644 §4.3 — Operations on groups: add members, remove members.
     */
    @Transactional
    public Object patchGroup(UUID id, List<Map<String, Object>> operations) {
        Optional<ScimGroup> opt = groupRepo.findById(id);
        if (opt.isEmpty()) {
            return ScimError.notFound("Group not found: " + id);
        }

        ScimGroup group = opt.get();
        Set<String> memberIds = new HashSet<>();

        // Parse existing members
        if (group.getMembers() != null && !group.getMembers().isBlank()) {
            for (String m : group.getMembers().split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isBlank()) memberIds.add(trimmed);
            }
        }

        // Apply PATCH operations
        for (Map<String, Object> op : operations) {
            String opType = (String) op.get("op");
            Object rawValue = op.get("path");
            List<Map<String, Object>> members;

            if ("add".equalsIgnoreCase(opType)) {
                members = (List<Map<String, Object>>) op.get("members");
                if (members != null) {
                    for (Map<String, Object> member : members) {
                        String value = (String) member.get("value");
                        if (value != null) memberIds.add(value);
                    }
                }
            } else if ("remove".equalsIgnoreCase(opType)) {
                members = (List<Map<String, Object>>) op.get("members");
                if (members != null) {
                    for (Map<String, Object> member : members) {
                        String value = (String) member.get("value");
                        if (value != null) memberIds.remove(value);
                    }
                }
            }
        }

        // Verify all member IDs exist
        for (String memberId : memberIds) {
            try {
                UUID uid = UUID.fromString(memberId);
                if (!userRepo.existsById(uid)) {
                    return ScimError.badRequest("User not found: " + memberId);
                }
            } catch (IllegalArgumentException e) {
                return ScimError.badRequest("Invalid user ID: " + memberId);
            }
        }

        group.setMembers(String.join(",", memberIds));
        ScimGroup saved = groupRepo.save(group);
        return toDto(saved, BASE_LOCATION + "/" + saved.getId());
    }

    /**
     * Delete a SCIM group.
     * DELETE /scim/v2/Groups/{id}
     */
    @Transactional
    public Object deleteGroup(UUID id) {
        if (!groupRepo.existsById(id)) {
            return ScimError.notFound("Group not found: " + id);
        }
        groupRepo.deleteById(id);
        return null;
    }

    private ScimGroupDto toDto(ScimGroup g, String location) {
        return ScimGroupDto.from(
            g.getId(),
            g.getDisplayName(),
            g.getMembers(),
            g.getExternalId(),
            g.getAttributes(),
            g.getCreatedAt(),
            g.getUpdatedAt(),
            location
        );
    }

    private String extractFilterValue(String filter) {
        int start = filter.indexOf('"');
        int end = filter.lastIndexOf('"');
        if (start >= 0 && end > start) {
            return filter.substring(start + 1, end);
        }
        return "";
    }

    public record CreateResult(ScimGroup group, String location, int status) {}
}
