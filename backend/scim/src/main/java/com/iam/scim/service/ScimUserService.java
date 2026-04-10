package com.iam.scim.service;

import com.iam.authcore.entity.ScimUser;
import com.iam.oauth.service.TokenService;
import com.iam.scim.dto.ScimError;
import com.iam.scim.dto.ScimListResponse;
import com.iam.scim.dto.ScimUserDto;
import com.iam.scim.repository.ScimUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * SCIM 2.0 User management per RFC 7644 §5.2.
 *
 * Supports: POST, GET, PUT, DELETE on /scim/v2/Users
 *
 * JML Lifecycle hooks (Task 23):
 * - Joiner (create): audit event with joiner flag
 * - Leaver (delete): all tokens for user revoked immediately
 */
@Service
public class ScimUserService {

    private static final Logger log = LoggerFactory.getLogger(ScimUserService.class);
    private static final String CONTENT_TYPE = "application/scim+json";
    private static final String BASE_LOCATION = "http://localhost:8080/scim/v2/Users";

    private final ScimUserRepository userRepo;
    private final TokenService tokenService;

    public ScimUserService(ScimUserRepository userRepo, TokenService tokenService) {
        this.userRepo = userRepo;
        this.tokenService = tokenService;
    }

    public String getContentType() { return CONTENT_TYPE; }

    /**
     * Create a new SCIM user.
     * POST /scim/v2/Users
     */
    @Transactional
    public Object createUser(ScimUserDto dto) {
        if (dto.userName() == null || dto.userName().isBlank()) {
            return ScimError.badRequest("userName is required");
        }

        if (userRepo.existsByUserName(dto.userName())) {
            return ScimError.conflict("userName already exists: " + dto.userName());
        }

        ScimUser user = new ScimUser();
        user.setUserName(dto.userName());
        user.setDisplayName(dto.displayName() != null ? dto.displayName() : "");
        user.setEmails(dto.emails() != null && !dto.emails().isEmpty()
            ? dto.emails().get(0).value() : "");
        user.setActive(dto.active() != null ? dto.active() : true);
        user.setGroups("");
        user.setExternalId(dto.externalId());
        user.setAttributes(dto.attributes());

        ScimUser saved = userRepo.save(user);
        String location = BASE_LOCATION + "/" + saved.getId();

        // JML Joiner lifecycle hook: fire-and-forget joiner audit event
        log.info("JML [joiner] user created: userName={}", saved.getUserName());

        return new CreateResult(saved, location, 201);
    }

    /**
     * List SCIM users with optional filtering.
     * GET /scim/v2/Users?filter=userName eq "..."&startIndex=1&count=10
     */
    public Object listUsers(String filter, int startIndex, int count) {
        Pageable pageable = PageRequest.of(
            Math.max(0, (startIndex - 1) / Math.max(count, 1)),
            count
        );

        Page<ScimUser> page;
        if (filter != null && filter.contains("userName")) {
            String value = extractFilterValue(filter);
            page = userRepo.findByUserNameContaining(value, pageable);
        } else {
            page = userRepo.findAll(pageable);
        }

        List<ScimUserDto> dtos = page.getContent().stream()
            .map(u -> toDto(u, BASE_LOCATION + "/" + u.getId()))
            .toList();

        return ScimListResponse.of(dtos, startIndex);
    }

    /**
     * Get a single SCIM user by ID.
     * GET /scim/v2/Users/{id}
     */
    public Object getUser(UUID id) {
        Optional<ScimUser> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return ScimError.notFound("User not found: " + id);
        }
        return toDto(opt.get(), BASE_LOCATION + "/" + opt.get().getId());
    }

    /**
     * Replace (PUT) a SCIM user.
     * PUT /scim/v2/Users/{id}
     */
    @Transactional
    public Object replaceUser(UUID id, ScimUserDto dto) {
        Optional<ScimUser> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return ScimError.notFound("User not found: " + id);
        }

        ScimUser user = opt.get();
        if (dto.userName() != null && !dto.userName().equals(user.getUserName())) {
            if (userRepo.existsByUserName(dto.userName())) {
                return ScimError.conflict("userName already exists: " + dto.userName());
            }
        }

        if (dto.userName() != null) user.setUserName(dto.userName());
        if (dto.displayName() != null) user.setDisplayName(dto.displayName());
        if (dto.emails() != null && !dto.emails().isEmpty()) {
            user.setEmails(dto.emails().get(0).value());
        }
        if (dto.active() != null) user.setActive(dto.active());
        if (dto.externalId() != null) user.setExternalId(dto.externalId());
        if (dto.attributes() != null) user.setAttributes(dto.attributes());

        ScimUser saved = userRepo.save(user);
        return toDto(saved, BASE_LOCATION + "/" + saved.getId());
    }

    /**
     * Delete a SCIM user.
     * DELETE /scim/v2/Users/{id}
     */
    @Transactional
    public Object deleteUser(UUID id) {
        Optional<ScimUser> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return ScimError.notFound("User not found: " + id);
        }
        ScimUser user = opt.get();

        // JML Leaver lifecycle hook: revoke all tokens before deletion
        String subject = user.getUserName();
        int revoked = tokenService.revokeAllTokensForUser(subject);
        log.info("JML [leaver] user deleted: userName={}, tokens_revoked={}", subject, revoked);

        userRepo.deleteById(id);
        return null; // 204 No Content
    }

    private ScimUserDto toDto(ScimUser u, String location) {
        return ScimUserDto.from(
            u.getId(),
            u.getUserName(),
            u.getDisplayName(),
            u.getEmails(),
            u.getActive(),
            u.getGroups(),
            u.getExternalId(),
            u.getAttributes(),
            u.getCreatedAt(),
            u.getUpdatedAt(),
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

    public record CreateResult(ScimUser user, String location, int status) {}
}
