package com.iam.authcore.repository;

import com.iam.authcore.entity.AuthCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;

public interface AuthCodeRepository extends JpaRepository<AuthCode, String> {
    Optional<AuthCode> findByCodeAndConsumedAtIsNull(String code);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthCode a SET a.consumedAt = :consumedAt WHERE a.code = :code")
    int markConsumed(String code, Instant consumedAt);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuthCode a WHERE a.expiresAt < :now")
    int deleteExpired(Instant now);
}
