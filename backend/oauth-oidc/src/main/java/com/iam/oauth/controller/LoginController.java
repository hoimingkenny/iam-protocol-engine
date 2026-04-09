package com.iam.oauth.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Simple login endpoint for Phase 5 Admin UI.
 *
 * POST /login
 *
 * Accepts username/password, validates credentials (simple in-memory for Phase 5),
 * and issues a login token stored in Redis. The login token is passed to
 * /authorize as the authenticated user identifier.
 *
 * In Phase 5, credentials are validated against a simple user store.
 * Phase 6 (SCIM) will replace this with a proper user directory.
 */
@RestController
public class LoginController {

    private static final String LOGIN_TOKEN_PREFIX = "login:";
    private static final Duration LOGIN_TOKEN_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    // Phase 5 simple user store — replace with SCIM user lookup in Phase 6
    private static final Map<String, String> USERS = Map.of(
        "admin", "admin123",
        "user1", "user1pass",
        "alice", "alicepass"
    );

    public LoginController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Authenticate user and return a login token.
     *
     * @param username the username
     * @param password the password
     * @return login token on success, 401 on failure
     */
    @PostMapping(value = "/login",
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(
            @RequestParam("username") String username,
            @RequestParam("password") String password
    ) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "invalid_credentials", "error_description", "username and password required"));
        }

        String storedPassword = USERS.get(username);
        if (storedPassword == null || !storedPassword.equals(password)) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "invalid_credentials", "error_description", "invalid username or password"));
        }

        // Issue login token — stored in Redis, valid for 10 minutes
        String loginToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(LOGIN_TOKEN_PREFIX + loginToken, username, LOGIN_TOKEN_TTL);

        return ResponseEntity.ok(Map.of(
            "login_token", loginToken,
            "username", username,
            "expires_in", LOGIN_TOKEN_TTL.toSeconds()
        ));
    }
}
