package com.iam.oauth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC Discovery endpoint per RFC 8414.
 *
 * GET /.well-known/openid-configuration
 *
 * Returns RFC 8414 §3 JSON metadata about the authorization server.
 * Consumers use this to discover endpoints, supported algorithms, etc.
 */
@RestController
public class DiscoveryController {

    private final String issuer;

    public DiscoveryController(@Value("${server.port:8080}") int port,
                               @Value("${server.servlet.context-path:}") String contextPath) {
        // Build issuer from server config — no trailing slash
        String base = "http://localhost:" + port;
        this.issuer = contextPath.isEmpty() ? base : base + contextPath;
    }

    /**
     * OIDC Discovery document per RFC 8414 §3.
     */
    @GetMapping(value = "/.well-known/openid-configuration",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openidConfiguration() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", issuer);
        m.put("authorization_endpoint", issuer + "/oauth2/authorize");
        m.put("token_endpoint", issuer + "/oauth2/token");
        m.put("jwks_uri", issuer + "/.well-known/jwks.json");
        m.put("response_types_supported", List.of("code"));
        m.put("subject_types_supported", List.of("public"));
        m.put("id_token_signing_alg_values_supported", List.of("RS256"));
        m.put("scopes_supported", List.of("openid", "profile", "email"));
        m.put("token_endpoint_auth_methods_supported",
            List.of("client_secret_basic", "client_secret_post", "none"));
        m.put("claims_supported",
            List.of("iss", "sub", "aud", "exp", "iat", "nonce", "name", "email", "scope"));
        m.put("grant_types_supported",
            List.of("authorization_code", "client_credentials", "refresh_token",
                    "urn:ietf:params:oauth:grant-type:device_code"));
        m.put("code_challenge_methods_supported", List.of("S256"));
        return m;
    }
}
