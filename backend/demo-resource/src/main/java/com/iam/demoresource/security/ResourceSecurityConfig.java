package com.iam.demoresource.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for demo-resource.
 *
 * All /api/** endpoints require a valid Bearer token.
 * /health and /actuator/** are publicly accessible.
 */
@Configuration
@EnableWebSecurity
public class ResourceSecurityConfig {

    private final TokenValidationService tokenValidationService;

    public ResourceSecurityConfig(TokenValidationService tokenValidationService) {
        this.tokenValidationService = tokenValidationService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(
                new BearerTokenAuthenticationFilter(tokenValidationService),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
