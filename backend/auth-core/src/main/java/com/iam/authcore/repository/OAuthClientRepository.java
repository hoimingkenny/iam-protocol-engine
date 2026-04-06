package com.iam.authcore.repository;

import com.iam.authcore.entity.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OAuthClientRepository extends JpaRepository<OAuthClient, String> {
    Optional<OAuthClient> findByClientId(String clientId);
}
