package com.iam.authcore.entity;

import com.iam.authcore.AuthCoreTestApplication;
import com.iam.authcore.repository.AuditEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthCoreTestApplication.class)
@ActiveProfiles("test")
@Transactional
class AuditEventTest {

    @Autowired
    private AuditEventRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void persistsAuditEventWithAllFields() {
        AuditEvent event = new AuditEvent();
        event.setEventType("token_issued");
        event.setActor("user-123");
        event.setSubject("jti-abc");
        event.setClientId("client-1");
        event.setScope("openid profile");
        event.setJti("jti-abc");
        event.setIpAddress("192.168.1.1");
        event.setUserAgent("Mozilla/5.0");
        event.setDetails(Map.of("grant_type", "authorization_code"));

        AuditEvent saved = repository.save(event);
        em.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void findByIdReturnsEvent() {
        AuditEvent event = new AuditEvent();
        event.setEventType("auth_code_consumed");
        event.setActor("user-456");
        event.setClientId("client-2");
        repository.save(event);
        em.flush();

        Optional<AuditEvent> found = repository.findById(event.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEventType()).isEqualTo("auth_code_consumed");
    }

    @Test
    void findAllByOrderByTimestampDescReturnsEventsInOrder() {
        AuditEvent e1 = new AuditEvent();
        e1.setEventType("event_1");
        e1.setActor("user-1");
        AuditEvent e2 = new AuditEvent();
        e2.setEventType("event_2");
        e2.setActor("user-2");
        repository.save(e1);
        repository.save(e2);
        em.flush();

        var page = repository.findAllByOrderByTimestampDesc(
            org.springframework.data.domain.Pageable.ofSize(10)
        );

        assertThat(page.getContent()).hasSize(2);
    }
}
