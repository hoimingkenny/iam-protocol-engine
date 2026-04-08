package com.iam.demoresource;

import com.iam.demoresource.controller.ResourceController;
import com.iam.demoresource.security.BearerTokenAuthenticationFilter;
import com.iam.demoresource.security.TokenValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for demo-resource security filter and controller.
 *
 * SC-14: Protected demo API rejects requests without valid Bearer token.
 */
class ResourceControllerTest {

    private TokenValidationService mockTokenService;
    private BearerTokenAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mockTokenService = mock(TokenValidationService.class);
        filter = new BearerTokenAuthenticationFilter(mockTokenService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    // --- Filter tests ---

    @Test
    void filter_withNoAuthHeader_doesNotSetAuthentication() throws Exception {
        request.setRequestURI("/api/resource");

        filter.doFilter(request, response, filterChain);

        // Filter passes through without setting auth when no Authorization header present
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        // Response status is 200 because MockFilterChain doesn't process the servlet
    }

    @Test
    void filter_withInvalidToken_setsNoAuthentication() throws Exception {
        when(mockTokenService.validate("invalid-token"))
            .thenReturn(TokenValidationService.ValidationResult.invalid());

        request.setRequestURI("/api/resource");
        request.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_withValidToken_setsAuthentication() throws Exception {
        when(mockTokenService.validate("valid-token"))
            .thenReturn(new TokenValidationService.ValidationResult(true, "user-123", "openid"));

        request.setRequestURI("/api/resource");
        request.addHeader("Authorization", "Bearer valid-token");

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user-123", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void filter_nonApiPath_doesNotSetAuthentication() throws Exception {
        request.setRequestURI("/health");

        filter.doFilter(request, response, filterChain);

        // Filter lets non-/api paths through without setting auth
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // --- Controller tests ---

    @Test
    void controller_withoutAuth_returnsMessage() {
        SecurityContextHolder.clearContext();
        ResourceController controller = new ResourceController();

        var response = controller.resource();

        assertEquals("You accessed a protected resource", response.getBody().get("message"));
        assertEquals("(client_credentials — no user subject)", response.getBody().get("subject"));
    }

    @Test
    void controller_withUserAuth_returnsUserSubject() {
        SecurityContextHolder.clearContext();
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "alice@example.com", null, java.util.List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResourceController controller = new ResourceController();
        var response = controller.resource();

        assertEquals("You accessed a protected resource", response.getBody().get("message"));
        assertEquals("alice@example.com", response.getBody().get("subject"));
    }

    @Test
    void controller_health_returnsOk() {
        ResourceController controller = new ResourceController();
        var response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ok", response.getBody().get("status"));
    }
}
