package com.iam.scim.repository;

import com.iam.authcore.entity.ScimGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID> {

    Optional<ScimGroup> findByDisplayName(String displayName);

    boolean existsByDisplayName(String displayName);

    @Query("SELECT g FROM ScimGroup g WHERE LOWER(g.displayName) LIKE LOWER(CONCAT('%', :filter, '%'))")
    Page<ScimGroup> findByDisplayNameContaining(@Param("filter") String filter, Pageable pageable);
}
