package com.iam.authcore.repository;

import com.iam.authcore.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, java.util.UUID> {
    Page<AuditEvent> findByEventTypeOrderByTimestampDesc(String eventType, Pageable pageable);
    Page<AuditEvent> findByActorOrderByTimestampDesc(String actor, Pageable pageable);
    Page<AuditEvent> findByClientIdOrderByTimestampDesc(String clientId, Pageable pageable);
    List<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to);
    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);
}
