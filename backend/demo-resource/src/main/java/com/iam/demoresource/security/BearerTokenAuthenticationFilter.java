package com.iam.demoresource.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts Bearer token from Authorization header and validates it
 * against the token registry (Phase 2: DB lookup; Phase 3: JWT validation).
 *
 * On success, sets the authentication in the SecurityContext.
 * On failure, leaves context empty — SecurityConfig will return 401.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenValidationService tokenValidationService;

    public BearerTokenAuthenticationFilter(TokenValidationService tokenValidationService) {
        this.tokenValidationService = tokenValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length()).trim();
            TokenValidationService.ValidationResult result = tokenValidationService.validate(token);

            if (result.isValid()) {
                List<SimpleGrantedAuthority> authorities =
                    java.util.Arrays.stream(result.scope().split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(result.subject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
