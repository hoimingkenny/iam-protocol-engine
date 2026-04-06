package com.iam.authcore.service;

import com.iam.authcore.entity.AuditEvent;
import java.util.Map;

public interface AuditService {

    /**
     * Log an audit event.
     *
     * @param eventType  RFC-compliant event type (e.g. "token_issued", "auth_code_consumed")
     * @param actor      User or client that triggered the event (may be null for system events)
     * @param subject    Resource affected (e.g. token jti, user id)
     * @param clientId   OAuth client identifier
     * @param scope      Granted/scoped resource
     * @param jti        Token identifier (if applicable)
     * @param ipAddress  Client IP address
     * @param details    Additional structured details
     */
    void audit(String eventType,
               String actor,
               String subject,
               String clientId,
               String scope,
               String jti,
               String ipAddress,
               Map<String, Object> details);

    AuditEvent save(AuditEvent event);
}
