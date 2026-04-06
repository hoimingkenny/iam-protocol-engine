package com.iam.authcore.repository;

import com.iam.authcore.entity.TotpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {
    Optional<TotpCredential> findByUserId(String userId);
    void deleteByUserId(String userId);
}
