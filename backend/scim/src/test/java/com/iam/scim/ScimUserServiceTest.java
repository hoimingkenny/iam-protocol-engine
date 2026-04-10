package com.iam.scim;

import com.iam.authcore.entity.ScimUser;
import com.iam.oauth.service.TokenService;
import com.iam.scim.dto.ScimError;
import com.iam.scim.dto.ScimUserDto;
import com.iam.scim.repository.ScimUserRepository;
import com.iam.scim.service.ScimUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScimUserServiceTest {

    private ScimUserRepository userRepo;
    private TokenService tokenService;
    private ScimUserService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(ScimUserRepository.class);
        tokenService = mock(TokenService.class);
        service = new ScimUserService(userRepo, tokenService);
    }

    private ScimUser persistedUser(String userName) {
        ScimUser u = new ScimUser();
        u.setId(UUID.randomUUID());
        u.setUserName(userName);
        u.setDisplayName("Test User");
        u.setEmails(userName + "@example.com");
        u.setActive(true);
        u.setGroups("");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }

    // --- createUser ---

    @Test
    void createUser_missingUserName_returnsError() {
        ScimUserDto dto = new ScimUserDto(null, "", null, null, null, null, null, null, null, null);
        Object result = service.createUser(dto);
        assertTrue(result instanceof ScimError);
        assertEquals(400, ((ScimError) result).status());
    }

    @Test
    void createUser_duplicateUserName_returnsConflict() {
        when(userRepo.existsByUserName("alice")).thenReturn(true);
        ScimUserDto dto = new ScimUserDto(null, "alice", null, "Alice", null, null, null, null, null, null);
        Object result = service.createUser(dto);
        assertTrue(result instanceof ScimError);
        assertEquals(409, ((ScimError) result).status());
    }

    @Test
    void createUser_valid_returnsCreated() {
        ScimUser saved = persistedUser("bob");
        when(userRepo.existsByUserName("bob")).thenReturn(false);
        when(userRepo.save(any(ScimUser.class))).thenReturn(saved);

        ScimUserDto dto = new ScimUserDto(
            null, "bob", null, "Bob Jones",
            List.of(new ScimUserDto.EmailDto("bob@example.com", "work", true)),
            true, null, null, null, null
        );
        Object result = service.createUser(dto);
        assertTrue(result instanceof ScimUserService.CreateResult);
    }

    // --- getUser ---

    @Test
    void getUser_notFound_returnsError() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());
        Object result = service.getUser(UUID.randomUUID());
        assertTrue(result instanceof ScimError);
        assertEquals(404, ((ScimError) result).status());
    }

    @Test
    void getUser_found_returnsDto() {
        UUID id = UUID.randomUUID();
        ScimUser u = persistedUser("carol");
        u.setId(id);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));

        Object result = service.getUser(id);
        assertTrue(result instanceof ScimUserDto);
        assertEquals("carol", ((ScimUserDto) result).userName());
    }

    // --- deleteUser ---

    @Test
    void deleteUser_notFound_returnsError() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());
        Object result = service.deleteUser(UUID.randomUUID());
        assertTrue(result instanceof ScimError);
        assertEquals(404, ((ScimError) result).status());
    }

    @Test
    void deleteUser_found_deletes() {
        UUID id = UUID.randomUUID();
        ScimUser user = persistedUser("alice");
        user.setId(id);
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        when(tokenService.revokeAllTokensForUser("alice")).thenReturn(2);
        Object result = service.deleteUser(id);
        assertNull(result);
        verify(tokenService).revokeAllTokensForUser("alice");
        verify(userRepo).deleteById(id);
    }
}
