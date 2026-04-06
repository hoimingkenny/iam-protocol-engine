package com.iam.authcore.entity;

import com.iam.authcore.AuthCoreTestApplication;
import com.iam.authcore.repository.ScimUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthCoreTestApplication.class)
@ActiveProfiles("test")
@Transactional
class ScimUserTest {

    @Autowired
    private ScimUserRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void persistsScimUserWithAllFields() {
        ScimUser user = new ScimUser();
        user.setUserName("john.doe");
        user.setEmails("john@example.com,john@corp.com");
        user.setDisplayName("John Doe");
        user.setActive(true);
        user.setGroups("engineering,product");
        user.setExternalId("ext-123");

        ScimUser saved = repository.save(user);
        em.flush();
        em.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUserName()).isEqualTo("john.doe");
    }

    @Test
    void findByUserNameReturnsUser() {
        ScimUser user = new ScimUser();
        user.setUserName("jane.doe");
        user.setEmails("jane@example.com");
        repository.save(user);
        em.flush();

        Optional<ScimUser> found = repository.findByUserName("jane.doe");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isNull();
    }

    @Test
    void updatedAtChangesOnUpdate() {
        ScimUser user = new ScimUser();
        user.setUserName("update.me");
        user.setEmails("update@example.com");
        repository.save(user);
        em.flush();

        var before = repository.findByUserName("update.me").get().getUpdatedAt();

        user.setDisplayName("Updated Name");
        repository.save(user);
        em.flush();

        assertThat(repository.findByUserName("update.me").get().getUpdatedAt())
            .isAfter(before);
    }
}
