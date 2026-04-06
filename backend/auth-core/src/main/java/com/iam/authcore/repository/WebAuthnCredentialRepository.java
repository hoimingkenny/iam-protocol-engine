package com.iam.authcore.repository;

import com.iam.authcore.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, String> {
    List<WebAuthnCredential> findByUserId(String userId);
    void deleteByUserId(String userId);
}
