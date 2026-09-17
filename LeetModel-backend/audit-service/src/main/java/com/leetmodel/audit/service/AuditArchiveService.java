package com.leetmodel.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.audit.metrics.AuditMetrics;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/** 在 audit-service 本地短事务中完成 Inbox 去重和只追加归档。 */
@Service
public class AuditArchiveService {
    public static final String CONSUMER_GROUP = "cg-audit-archive-v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditMetrics metrics;

    public AuditArchiveService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                               AuditMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public ArchiveResult archive(MessageEnvelopeV1<OperationAuditPayloadV1> envelope) {
        OperationAuditPayloadV1 payload = envelope.payload();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO message_inbox
                      (consumer_group,event_id,event_type,source_service,trace_id,status,
                       occurred_at,create_time,update_time)
                    VALUES (?,?,?,?,?,'PROCESSING',?,?,?)
                    """,
                    CONSUMER_GROUP, envelope.eventId(), envelope.eventType(),
                    envelope.sourceService(), envelope.traceId(),
                    Timestamp.from(envelope.occurredAt()), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException duplicate) {
            metrics.duplicate();
            return ArchiveResult.DUPLICATE;
        }

        jdbcTemplate.update("""
                INSERT INTO operation_audit_event (
                  audit_event_id,audit_schema_version,operation_id,phase,occurred_at,
                  source_service,service_version,category,operation_code,risk_level,outcome,
                  reason,failure_code,actor_type,actor_id,actor_roles_json,target_type,target_id,
                  target_version,before_summary_json,after_summary_json,trace_id,sw_trace_id,
                  request_id,domain_task_id,related_event_id,client_ip_hash,user_agent_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                payload.auditEventId(), payload.auditSchemaVersion(), payload.operationId(),
                payload.phase(), Timestamp.from(payload.occurredAt()), payload.sourceService(),
                payload.serviceVersion(), payload.category(), payload.operationCode(),
                payload.riskLevel(), payload.outcome(), payload.reason(), payload.failureCode(),
                payload.actorType(), payload.actorId(), json(payload.actorRolesSnapshot()),
                payload.targetType(), payload.targetId(), payload.targetVersion(),
                json(payload.beforeSummary()), json(payload.afterSummary()), payload.traceId(),
                payload.swTraceId(), payload.requestId(), payload.domainTaskId(),
                payload.relatedEventId(), payload.clientIpHash(), payload.userAgentHash());
        jdbcTemplate.update("""
                UPDATE message_inbox SET status='CONSUMED', consumed_at=?, update_time=?
                WHERE consumer_group=? AND event_id=?
                """, Timestamp.from(now), Timestamp.from(now), CONSUMER_GROUP, envelope.eventId());
        metrics.consumed();
        return ArchiveResult.CONSUMED;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("audit summary cannot be serialized", exception);
        }
    }

    public enum ArchiveResult { CONSUMED, DUPLICATE }
}
