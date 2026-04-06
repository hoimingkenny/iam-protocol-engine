package com.iam.authcore.repository;

import com.iam.authcore.entity.DirectoryLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectoryLinkRepository extends JpaRepository<DirectoryLink, UUID> {
    Optional<DirectoryLink> findByUserIdAndDirectorySource(String userId, String source);
    List<DirectoryLink> findByUserId(String userId);
    void deleteByUserId(String userId);
}
