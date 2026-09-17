package com.leetmodel.audit.monitor;

import com.leetmodel.audit.metrics.AuditMetrics;
import com.leetmodel.common.api.audit.OperationAuditCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/** 定期计算审计 Inbox 积压和缺少 COMPLETED 终态的操作，不执行任何业务重做。 */
@Component
public class AuditIntegrityMonitor {
    private final JdbcTemplate jdbcTemplate;
    private final AuditMetrics metrics;

    public AuditIntegrityMonitor(JdbcTemplate jdbcTemplate, AuditMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${leetmodel.audit.monitor-delay-ms:30000}")
    public void refresh() {
        try {
            Long processing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM message_inbox WHERE status='PROCESSING'", Long.class);
            metrics.inboxProcessing(processing == null ? 0 : processing);

            var pending = jdbcTemplate.query("""
                    SELECT operation_id, operation_code, occurred_at
                    FROM operation_audit_event
                    WHERE phase='REQUESTED' AND outcome='PENDING'
                    """, (rs, row) -> new Pending(
                    rs.getString("operation_id"), rs.getString("operation_code"),
                    rs.getTimestamp("occurred_at")));
            long incomplete = pending.stream()
                    .filter(this::pastDeadlineWithoutTerminal)
                    .filter(item -> !hasTerminal(item.operationId()))
                    .count();
            metrics.incomplete(incomplete);
        } catch (RuntimeException exception) {
            metrics.monitorFailure();
        }
    }

    private boolean pastDeadlineWithoutTerminal(Pending pending) {
        long deadline = OperationAuditCatalog.require(pending.operationCode())
                .completionDeadlineSeconds();
        return deadline > 0 && pending.occurredAt() != null
                && Duration.between(pending.occurredAt().toInstant(), Instant.now()).getSeconds() > deadline;
    }

    private boolean hasTerminal(String operationId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operation_audit_event
                WHERE operation_id=? AND phase='COMPLETED'
                """, Long.class, operationId);
        return count != null && count > 0;
    }

    private record Pending(String operationId, String operationCode, Timestamp occurredAt) { }
}
