package com.iam.oauth.controller;

import com.iam.authcore.entity.Token;
import com.iam.authcore.repository.TokenRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Token Revocation endpoint per RFC 7009.
 *
 * POST /oauth2/revoke
 *
 * Allows a client to inform the authorization server that it no longer needs
 * a token. The server revokes the token immediately.
 *
 * RFC 7009 §2.1: The authorization server responds with 200 even if the
 * token was invalid or already revoked — this prevents token enumeration attacks.
 */
@RestController
@RequestMapping("/oauth2")
public class RevocationController {

    private final TokenRepository tokenRepo;

    public RevocationController(TokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    /**
     * Token revocation per RFC 7009 §2.1.
     *
     * @param token          The token to revoke (required)
     * @param tokenTypeHint  Optional hint about the token type
     * @return 200 OK (always, per RFC 7009 §2.1)
     */
    @Transactional
    @PostMapping(value = "/revoke",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(
            @RequestParam("token") String token,
            @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
    ) {
        if (token != null && !token.isBlank()) {
            revokeToken(token);
        }
        // Always return 200 per RFC 7009 §2.1 — don't reveal whether token existed
        return ResponseEntity.ok().build();
    }

    private void revokeToken(String tokenValue) {
        Optional<Token> optToken = tokenRepo.findByJtiAndRevokedFalse(tokenValue);
        if (optToken.isPresent()) {
            Token t = optToken.get();
            t.setRevoked(true);
            tokenRepo.save(t);

            // If this is a refresh token with a family, revoke the entire family
            // to prevent continued use of paired access token
            if (t.getType() == Token.TokenType.refresh_token && t.getFamilyId() != null) {
                tokenRepo.revokeAllByFamilyId(t.getFamilyId());
            }
        }
    }
}
