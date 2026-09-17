package com.leetmodel.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.OperationAuditEventDTO;
import com.leetmodel.common.api.dto.OperationAuditPageDTO;
import com.leetmodel.common.api.dto.OperationAuditQueryDTO;
import com.leetmodel.common.api.audit.OperationAuditCatalog;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 只读审计查询；字段、排序和分页上限均为固定白名单。 */
@Repository
public class AuditQueryRepository {
    private static final int MAX_LIMIT = 100;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public OperationAuditPageDTO search(OperationAuditQueryDTO query) {
        validateEnums(query);
        int limit = query.limit() == null ? 50 : query.limit();
        if (limit < 1 || limit > MAX_LIMIT) throw invalid("limit must be 1..100");
        StringBuilder sql = new StringBuilder("""
                SELECT audit_event_id,operation_id,phase,occurred_at,source_service,service_version,
                       category,operation_code,risk_level,outcome,reason,failure_code,actor_type,actor_id,
                       actor_roles_json,target_type,target_id,target_version,before_summary_json,
                       after_summary_json,trace_id,sw_trace_id,request_id,domain_task_id,related_event_id,
                       archived_at
                FROM operation_audit_event WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        exact(sql, args, "source_service", query.sourceService(), 100);
        exact(sql, args, "category", query.category(), 64);
        exact(sql, args, "operation_code", query.operationCode(), 100);
        exact(sql, args, "risk_level", query.riskLevel(), 16);
        exact(sql, args, "actor_id", query.actorId(), 100);
        exact(sql, args, "target_type", query.targetType(), 64);
        exact(sql, args, "target_id", query.targetId(), 100);
        exact(sql, args, "outcome", query.outcome(), 16);
        exact(sql, args, "operation_id", query.operationId(), 100);
        exact(sql, args, "trace_id", query.traceId(), 100);
        exact(sql, args, "sw_trace_id", query.swTraceId(), 100);
        if (query.from() != null) { sql.append(" AND occurred_at >= ?"); args.add(Timestamp.from(query.from())); }
        if (query.to() != null) { sql.append(" AND occurred_at < ?"); args.add(Timestamp.from(query.to())); }
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        sql.append(" ORDER BY occurred_at DESC, audit_event_id DESC LIMIT ?");
        args.add(limit + 1);
        List<OperationAuditEventDTO> rows = jdbcTemplate.query(sql.toString(), (rs, row) -> new OperationAuditEventDTO(
                rs.getString("audit_event_id"), rs.getString("operation_id"), rs.getString("phase"),
                instant(rs.getTimestamp("occurred_at")), rs.getString("source_service"),
                rs.getString("service_version"), rs.getString("category"), rs.getString("operation_code"),
                rs.getString("risk_level"), rs.getString("outcome"), rs.getString("reason"),
                rs.getString("failure_code"), rs.getString("actor_type"), rs.getString("actor_id"),
                readList(rs.getString("actor_roles_json")), rs.getString("target_type"),
                rs.getString("target_id"), rs.getString("target_version"),
                readMap(rs.getString("before_summary_json")), readMap(rs.getString("after_summary_json")),
                rs.getString("trace_id"), rs.getString("sw_trace_id"), rs.getString("request_id"),
                rs.getString("domain_task_id"), rs.getString("related_event_id"),
                instant(rs.getTimestamp("archived_at"))), args.toArray());
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, limit));
        return new OperationAuditPageDTO(List.copyOf(rows), hasMore, rows.size());
    }

    private void validateEnums(OperationAuditQueryDTO query) {
        if (query.operationCode() != null && !query.operationCode().isBlank()) {
            try {
                OperationAuditCatalog.require(query.operationCode());
            } catch (RuntimeException exception) {
                throw invalid("unsupported operationCode");
            }
        }
        if (query.riskLevel() != null && !List.of("LOW", "MEDIUM", "HIGH").contains(query.riskLevel())) {
            throw invalid("unsupported riskLevel");
        }
        if (query.outcome() != null && !List.of("PENDING", "SUCCEEDED", "FAILED", "REJECTED").contains(query.outcome())) {
            throw invalid("unsupported outcome");
        }
        if (query.category() != null && !query.category().isBlank()
                && OperationAuditCatalog.all().values().stream().noneMatch(s -> s.category().equals(query.category()))) {
            throw invalid("unsupported category");
        }
        if (query.sourceService() != null && !query.sourceService().isBlank()
                && OperationAuditCatalog.all().values().stream()
                .noneMatch(s -> s.sourceServices().contains(query.sourceService()))) {
            throw invalid("unsupported sourceService");
        }
    }

    private void exact(StringBuilder sql, List<Object> args, String column, String value, int max) {
        if (value == null || value.isBlank()) return;
        if (value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw invalid(column + " contains unsupported characters");
        }
        sql.append(" AND ").append(column).append(" = ?");
        args.add(value);
    }

    private Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    private List<String> readList(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() { }); }
        catch (Exception e) { throw new IllegalStateException("audit roles projection invalid", e); }
    }
    private Map<String, String> readMap(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { }); }
        catch (Exception e) { throw new IllegalStateException("audit summary projection invalid", e); }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCodeEnum.PARAM_INVALID, message);
    }
}
