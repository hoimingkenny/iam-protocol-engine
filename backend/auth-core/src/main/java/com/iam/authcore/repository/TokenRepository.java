package com.iam.authcore.repository;

import com.iam.authcore.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, String> {
    Optional<Token> findByJtiAndRevokedFalse(String jti);

    @Query("SELECT t FROM Token t WHERE t.subject = :subject AND t.revoked = false AND t.expiresAt > :now")
    List<Token> findActiveTokensBySubject(@Param("subject") String subject, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Token t SET t.revoked = true WHERE t.jti = :jti")
    int revokeByJti(@Param("jti") String jti);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Token t SET t.revoked = true WHERE t.subject = :subject")
    int revokeAllBySubject(@Param("subject") String subject);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Token t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
