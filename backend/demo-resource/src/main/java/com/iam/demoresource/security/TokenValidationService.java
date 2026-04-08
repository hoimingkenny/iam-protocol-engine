package com.iam.demoresource.security;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Token validation for the demo-resource.
 *
 * Phase 2: DB lookup via TokenRepository (opaque bearer token).
 * Phase 3: RS256 JWT validation via JWKS — replace this service.
 */
@Service
public class TokenValidationService {

    private final TokenRepository tokenRepo;

    public TokenValidationService(TokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    /**
     * Validate an opaque bearer token.
     *
     * @param jti  the token identifier (access_token value)
     * @return validation result with subject and scopes if valid
     */
    public ValidationResult validate(String jti) {
        if (jti == null || jti.isBlank()) {
            return ValidationResult.invalid();
        }

        Optional<Token> optToken = tokenRepo.findByJtiAndRevokedFalse(jti);
        if (optToken.isEmpty()) {
            return ValidationResult.invalid();
        }

        Token token = optToken.get();

        // Check expiry
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(Instant.now())) {
            return ValidationResult.invalid();
        }

        // Check it's an access token
        if (token.getType() != Token.TokenType.access_token) {
            return ValidationResult.invalid();
        }

        return new ValidationResult(true, token.getSubject() != null ? token.getSubject() : "",
            token.getScope() != null ? token.getScope() : "");
    }

    public record ValidationResult(boolean valid, String subject, String scope) {
        public boolean isValid() { return valid; }

        public static ValidationResult invalid() {
            return new ValidationResult(false, "", "");
        }
    }
}
