package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于消费服务本地数据库的事务 Inbox。
 */
public final class JdbcMessageInbox implements MessageInbox {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MessagingNamespace namespace;
    private final Clock clock;

    /**
     * 创建 Inbox。
     *
     * @param jdbcTemplate 本服务数据源
     * @param transactionTemplate 本地事务模板
     * @param namespace 资源命名空间
     * @param clock 时间源
     */
    public JdbcMessageInbox(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            MessagingNamespace namespace,
            Clock clock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public InboxResult executeOnce(
            String logicalConsumerGroup,
            MessageEnvelopeV1<?> envelope,
            Runnable domainAction
    ) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            try {
                jdbcTemplate.update("""
                        INSERT INTO message_inbox
                            (consumer_group, event_id, event_type, source_service, trace_id, status,
                             occurred_at, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, ?, ?)
                        """,
                        namespace.consumerGroup(logicalConsumerGroup),
                        envelope.eventId(),
                        envelope.eventType(),
                        envelope.sourceService(),
                        envelope.traceId(),
                        Timestamp.from(envelope.occurredAt()),
                        Timestamp.from(now),
                        Timestamp.from(now)
                );
            } catch (DuplicateKeyException exception) {
                return InboxResult.DUPLICATE;
            }

            domainAction.run();
            jdbcTemplate.update("""
                    UPDATE message_inbox
                    SET status = 'CONSUMED', consumed_at = ?, update_time = ?
                    WHERE consumer_group = ? AND event_id = ?
                    """,
                    Timestamp.from(Instant.now(clock)),
                    Timestamp.from(Instant.now(clock)),
                    namespace.consumerGroup(logicalConsumerGroup),
                    envelope.eventId()
            );
            return InboxResult.CONSUMED;
        });
    }

    /**
     * 查询消费去重记录与执行状态元数据（不读取业务领域载荷）。
     *
     * @param service 本微服务名称标识
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   单次拉取数量上限
     * @return 符合筛选条件的 Inbox 记录 DTO 列表
     */
    public List<MessagingInboxRecordDTO> findOperations(
            String service,
            String traceId,
            String eventId,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT consumer_group, event_id, event_type, source_service, trace_id, status,
                       occurred_at, consumed_at, update_time
                FROM message_inbox WHERE 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        appendExact(sql, arguments, "trace_id", traceId, 100);
        appendExact(sql, arguments, "event_id", eventId, 36);
        sql.append(" ORDER BY create_time DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNumber) -> new MessagingInboxRecordDTO(
                service,
                resultSet.getString("consumer_group"),
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getString("source_service"),
                resultSet.getString("trace_id"),
                resultSet.getString("status"),
                localDateTime(resultSet.getTimestamp("occurred_at")),
                localDateTime(resultSet.getTimestamp("consumed_at")),
                localDateTime(resultSet.getTimestamp("update_time"))
        ), arguments.toArray());
    }

    /**
     * 返回本微服务已成功完成本地事务消费的 Inbox 记录总数。
     *
     * @return 已完成消费的记录数量
     */
    public long consumedCount() {
        return count("CONSUMED");
    }

    /**
     * 返回本微服务指定固定状态的 Inbox 记录数。
     *
     * @param status 目标状态（PROCESSING 或 CONSUMED）
     * @return 符合该状态的记录总数
     * @throws IllegalArgumentException 若状态不受支持
     */
    public long count(String status) {
        if (!List.of("PROCESSING", "CONSUMED").contains(status)) {
            throw new IllegalArgumentException("unsupported inbox status");
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_inbox WHERE status = ?", Long.class, status);
        return count == null ? 0L : count;
    }

    /**
     * 返回最老处于处理中状态（PROCESSING）的 Inbox 事务存活秒数。
     *
     * @return 最老事务存活秒数；若无处理中事务则返回 0
     */
    public long oldestProcessingAgeSeconds() {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT MIN(create_time) FROM message_inbox WHERE status = 'PROCESSING'",
                Timestamp.class);
        if (timestamp == null) {
            return 0L;
        }
        return Math.max(0L, java.time.Duration.between(
                timestamp.toInstant(), Instant.now(clock)).toSeconds());
    }

    /**
     * 为动态 SQL 拼接严格的等值查询条件与参数校验。
     *
     * @param sql       SQL 构建器
     * @param arguments 参数列表
     * @param column    列名
     * @param value     查询值
     * @param maxLength 允许的最大字符长度
     */
    private void appendExact(
            StringBuilder sql,
            List<Object> arguments,
            String column,
            String value,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength || !trimmed.matches("[a-zA-Z0-9:._-]+")) {
            throw new IllegalArgumentException(column + " contains unsupported characters");
        }
        sql.append(" AND ").append(column).append(" = ?");
        arguments.add(trimmed);
    }

    /**
     * 将 JDBC Timestamp 安全转换为本地 LocalDateTime。
     *
     * @param value SQL 时间戳
     * @return 转换后的 LocalDateTime 实例，空值保持为 null
     */
    private java.time.LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
