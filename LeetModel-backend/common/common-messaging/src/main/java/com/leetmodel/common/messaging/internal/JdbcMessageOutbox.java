package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/**
 * 基于业务服务本地数据库的事务 Outbox。
 */
public final class JdbcMessageOutbox implements MessageOutbox {

    private final JdbcTemplate jdbcTemplate;
    private final MessageCodec codec;
    private final MessagingNamespace namespace;
    private final Clock clock;

    /**
     * 创建 Outbox。
     *
     * @param jdbcTemplate 本服务数据源
     * @param codec 消息编解码器
     * @param namespace 资源命名空间
     * @param clock 时间源
     */
    public JdbcMessageOutbox(
            JdbcTemplate jdbcTemplate,
            MessageCodec codec,
            MessagingNamespace namespace,
            Clock clock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String enqueue(
            String logicalTopic,
            String tag,
            MessageEnvelopeV1<?> envelope
    ) {
        byte[] body = codec.encode(envelope);
        Instant now = Instant.now(clock);
        jdbcTemplate.update("""
                INSERT INTO message_outbox
                    (event_id, topic, tag, message_key, event_type, schema_version,
                     source_service, aggregate_type, aggregate_id, idempotency_key,
                     trace_id, payload_json, status, retry_count, next_attempt_at,
                     occurred_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                """,
                envelope.eventId(),
                namespace.topic(logicalTopic),
                requiredTag(tag),
                envelope.eventId(),
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.sourceService(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.idempotencyKey(),
                envelope.traceId(),
                new String(body, StandardCharsets.UTF_8),
                Timestamp.from(now),
                Timestamp.from(envelope.occurredAt()),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return envelope.eventId();
    }

    /**
     * 使用条件更新领取一批消息；过期租约可被其他实例恢复。
     *
     * @param owner Relay 实例标识
     * @param limit 最大数量
     * @param lease 租约时长
     * @return 当前实例实际领取的消息
     */
    public List<PendingMessage> claim(String owner, int limit, Duration lease) {
        return claimObserved(owner, limit, lease).stream()
                .map(ClaimedOutboxMessage::message)
                .toList();
    }

    /**
     * 领取消息并保留普通领取与过期租约接管的区别。
     *
     * @param owner Relay 实例标识
     * @param limit 最大数量
     * @param lease 租约时长
     * @return 带租约来源的领取结果
     */
    List<ClaimedOutboxMessage> claimObserved(String owner, int limit, Duration lease) {
        Instant now = Instant.now(clock);
        Instant leaseExpiresAt = now.plus(lease);
        List<ClaimCandidate> candidates = jdbcTemplate.query("""
                SELECT event_id, status
                FROM message_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                   OR (status = 'SENDING' AND lease_expires_at < ?)
                ORDER BY create_time
                LIMIT ?
                """, (resultSet, rowNumber) -> new ClaimCandidate(
                        resultSet.getString("event_id"),
                        "SENDING".equals(resultSet.getString("status"))),
                Timestamp.from(now), Timestamp.from(now), limit * 2);

        List<ClaimedOutboxMessage> claimed = new ArrayList<>();
        for (ClaimCandidate candidate : candidates) {
            String claimCondition = candidate.takeover()
                    ? "status = 'SENDING' AND lease_expires_at < ?"
                    : "status = 'PENDING' AND next_attempt_at <= ?";
            String claimSql = """
                    UPDATE message_outbox
                    SET status = 'SENDING', lease_owner = ?, lease_expires_at = ?, update_time = ?
                    WHERE event_id = ?
                      AND (%s)
                    """.formatted(claimCondition);
            int changed = jdbcTemplate.update(claimSql,
                    owner,
                    Timestamp.from(leaseExpiresAt),
                    Timestamp.from(now),
                    candidate.eventId(),
                    Timestamp.from(now)
            );
            if (changed == 1) {
                claimed.add(new ClaimedOutboxMessage(
                        findClaimed(candidate.eventId(), owner), candidate.takeover()));
            }
            if (claimed.size() >= limit) {
                break;
            }
        }
        return claimed;
    }

    /**
     * 在真正发送单条消息前续租，避免大批量等待使批尾租约过期。
     *
     * @param eventId 事件标识
     * @param owner 当前租约所有者
     * @param lease 新租约时长
     * @return 当前实例是否仍拥有该消息
     */
    public boolean renewLease(String eventId, String owner, Duration lease) {
        Instant now = Instant.now(clock);
        int changed = jdbcTemplate.update("""
                UPDATE message_outbox
                SET lease_expires_at = ?, update_time = ?
                WHERE event_id = ? AND status = 'SENDING' AND lease_owner = ?
                """,
                Timestamp.from(now.plus(lease)),
                Timestamp.from(now),
                eventId,
                owner
        );
        return changed == 1;
    }

    /**
     * 标记 Broker 已确认接收。
     *
     * @param eventId 事件标识
     * @param owner 当前租约所有者
     * @param brokerMessageId Broker 消息标识
     */
    public void markPublished(String eventId, String owner, String brokerMessageId) {
        int changed = jdbcTemplate.update("""
                UPDATE message_outbox
                SET status = 'PUBLISHED', broker_message_id = ?, published_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, last_error = NULL, update_time = ?
                WHERE event_id = ? AND status = 'SENDING' AND lease_owner = ?
                """,
                brokerMessageId,
                Timestamp.from(Instant.now(clock)),
                Timestamp.from(Instant.now(clock)),
                eventId,
                owner
        );
        requireLease(changed, eventId);
    }

    /**
     * 记录瞬时错误并安排退避重试。
     *
     * @param eventId 事件标识
     * @param owner 当前租约所有者
     * @param nextAttemptAt 下次发送时间
     * @param error 脱敏错误摘要
     */
    public void markRetry(String eventId, String owner, Instant nextAttemptAt, String error) {
        int changed = jdbcTemplate.update("""
                UPDATE message_outbox
                SET status = 'PENDING', retry_count = retry_count + 1, next_attempt_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, last_error = ?, update_time = ?
                WHERE event_id = ? AND status = 'SENDING' AND lease_owner = ?
                """,
                Timestamp.from(nextAttemptAt),
                errorSummary(error),
                Timestamp.from(Instant.now(clock)),
                eventId,
                owner
        );
        requireLease(changed, eventId);
    }

    /**
     * 将稳定配置或契约错误转为阻塞状态。
     *
     * @param eventId 事件标识
     * @param owner 当前租约所有者
     * @param error 脱敏错误摘要
     */
    public void markBlocked(String eventId, String owner, String error) {
        int changed = jdbcTemplate.update("""
                UPDATE message_outbox
                SET status = 'BLOCKED', retry_count = retry_count + 1,
                    lease_owner = NULL, lease_expires_at = NULL, last_error = ?, update_time = ?
                WHERE event_id = ? AND status = 'SENDING' AND lease_owner = ?
                """,
                errorSummary(error),
                Timestamp.from(Instant.now(clock)),
                eventId,
                owner
        );
        requireLease(changed, eventId);
    }

    /**
     * 返回指定状态记录数。
     *
     * @param status Outbox 状态
     * @return 记录数
     */
    public long count(OutboxStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_outbox WHERE status = ?",
                Long.class,
                status.name()
        );
        return count == null ? 0L : count;
    }

    /**
     * 返回最老待发送消息的等待秒数。
     *
     * @return 秒数；没有待发送消息时为零
     */
    public long oldestPendingAgeSeconds() {
        return oldestAgeSeconds(List.of(OutboxStatus.PENDING, OutboxStatus.SENDING));
    }

    /**
     * 返回指定状态中最老记录的年龄。
     *
     * @param statuses 固定 Outbox 状态集合
     * @return 秒数；没有记录时为零
     */
    public long oldestAgeSeconds(List<OutboxStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0L;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(statuses.size(), "?"));
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT MIN(create_time) FROM message_outbox WHERE status IN (" + placeholders + ")",
                Timestamp.class,
                statuses.stream().map(Enum::name).toArray());
        if (timestamp == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(timestamp.toInstant(), Instant.now(clock)).toSeconds());
    }

    /**
     * 查询不含消息正文与敏感业务键的 Outbox 运维元数据。
     *
     * @param service 本服务名称标识
     * @param status  可选的状态筛选过滤条件
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   查询记录数量上限
     * @return 符合筛选条件的运维记录 DTO 列表
     */
    public List<MessagingOutboxRecordDTO> findOperations(
            String service,
            String status,
            String traceId,
            String eventId,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, topic, tag, event_type, aggregate_type, aggregate_id, trace_id,
                       status, retry_count, last_error, occurred_at, published_at, update_time
                FROM message_outbox WHERE 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            OutboxStatus.valueOf(normalized);
            sql.append(" AND status = ?");
            arguments.add(normalized);
        }
        appendExact(sql, arguments, "trace_id", traceId, 100);
        appendExact(sql, arguments, "event_id", eventId, 36);
        sql.append(" ORDER BY create_time DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNumber) -> new MessagingOutboxRecordDTO(
                service,
                resultSet.getString("event_id"),
                resultSet.getString("topic"),
                resultSet.getString("tag"),
                resultSet.getString("event_type"),
                resultSet.getString("aggregate_type"),
                resultSet.getString("aggregate_id"),
                resultSet.getString("trace_id"),
                resultSet.getString("status"),
                resultSet.getInt("retry_count"),
                resultSet.getString("last_error"),
                localDateTime(resultSet.getTimestamp("occurred_at")),
                localDateTime(resultSet.getTimestamp("published_at")),
                localDateTime(resultSet.getTimestamp("update_time"))
        ), arguments.toArray());
    }

    /**
     * 将已发布（含进入 DLQ 的原事件）或已阻塞事件重新置为待发送。
     * 事件 ID 和消息正文保持不变，消费端继续由 Inbox 保证幂等。
     *
     * @param eventIds 待重新发布的事件唯一 ID 列表，单次上限 20 个
     * @param reason   人工触发重放的审计原因说明
     * @return 实际被成功更新并重置为 PENDING 状态的事件 ID 列表
     */
    public List<String> replay(List<String> eventIds, String reason) {
        Instant now = Instant.now(clock);
        List<String> accepted = new ArrayList<>();
        for (String eventId : eventIds.stream().distinct().limit(20).toList()) {
            if (eventId == null || !eventId.matches("[0-9a-fA-F-]{36}")) {
                continue;
            }
            int changed = jdbcTemplate.update("""
                    UPDATE message_outbox
                    SET status = 'PENDING', next_attempt_at = ?, lease_owner = NULL,
                        lease_expires_at = NULL, broker_message_id = NULL, published_at = NULL,
                        last_error = ?, update_time = ?
                    WHERE event_id = ? AND status IN ('PUBLISHED', 'BLOCKED')
                    """,
                    Timestamp.from(now), errorSummary("operator replay: " + reason),
                    Timestamp.from(now), eventId);
            if (changed == 1) {
                accepted.add(eventId);
            }
        }
        return accepted;
    }

    /**
     * 精确查询已成功被当前 Relay 实例领取的待发消息明细。
     *
     * @param eventId 事件唯一 ID
     * @param owner   当前租约所有者实例标识
     * @return 待发送消息对象
     */
    private PendingMessage findClaimed(String eventId, String owner) {
        return jdbcTemplate.queryForObject("""
                SELECT event_id, topic, tag, message_key, event_type, payload_json,
                       retry_count, occurred_at
                FROM message_outbox
                WHERE event_id = ? AND status = 'SENDING' AND lease_owner = ?
                """,
                (resultSet, rowNumber) -> new PendingMessage(
                        resultSet.getString("event_id"),
                        resultSet.getString("topic"),
                        resultSet.getString("tag"),
                        resultSet.getString("message_key"),
                        resultSet.getString("event_type"),
                        resultSet.getString("payload_json"),
                        resultSet.getInt("retry_count"),
                        resultSet.getTimestamp("occurred_at").toInstant()
                ),
                eventId,
                owner
        );
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

    /**
     * 严格校验并归一化 RocketMQ Tag 字符合法性。
     *
     * @param tag 原始 Tag 字符串
     * @return 校验通过的修剪后 Tag 字符串
     * @throws IllegalArgumentException 若 tag 包含非法字符或为空
     */
    private String requiredTag(String tag) {
        String value = Objects.requireNonNull(tag, "tag").trim();
        if (value.isEmpty() || !value.matches("[A-Z0-9_]{1,80}")) {
            throw new IllegalArgumentException("tag contains unsupported characters");
        }
        return value;
    }

    private record ClaimCandidate(String eventId, boolean takeover) {
    }

    /**
     * 将错误堆栈摘要化并去除换行，防止日志或数据库列注入。
     *
     * @param error 原始错误字符串
     * @return 截断至 500 字符的安全单行摘要
     */
    private String errorSummary(String error) {
        String value = error == null ? "Unknown" : error.replaceAll("[\\r\\n]+", " ");
        return value.substring(0, Math.min(value.length(), 500));
    }

    /**
     * 断言乐观租约更新影响行数为 1，防止在丢失所有权后破坏性修改记录。
     *
     * @param changed 数据库更新受影响行数
     * @param eventId 关联的事件唯一标识
     * @throws IllegalStateException 若更新行数不为 1（表明租约已丢失）
     */
    private void requireLease(int changed, String eventId) {
        if (changed != 1) {
            throw new IllegalStateException("outbox lease lost: " + eventId);
        }
    }
}
