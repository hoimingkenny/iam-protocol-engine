package com.iam.authcore.repository;

import com.iam.authcore.entity.ScimUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ScimUserRepository extends JpaRepository<ScimUser, UUID> {
    Optional<ScimUser> findByUserName(String userName);
    boolean existsByUserName(String userName);
    Optional<ScimUser> findByExternalId(String externalId);
}
