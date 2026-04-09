package com.iam.scim.repository;

import com.iam.authcore.entity.ScimUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScimUserRepository extends JpaRepository<ScimUser, UUID> {

    Optional<ScimUser> findByUserName(String userName);

    boolean existsByUserName(String userName);

    void deleteByUserName(String userName);

    @Query("SELECT u FROM ScimUser u WHERE LOWER(u.userName) LIKE LOWER(CONCAT('%', :filter, '%'))")
    Page<ScimUser> findByUserNameContaining(@Param("filter") String filter, Pageable pageable);
}
