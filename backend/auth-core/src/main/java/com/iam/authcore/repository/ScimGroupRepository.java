package com.iam.authcore.repository;

import com.iam.authcore.entity.ScimGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID> {
    Optional<ScimGroup> findByDisplayName(String displayName);
    Optional<ScimGroup> findByExternalId(String externalId);
}
