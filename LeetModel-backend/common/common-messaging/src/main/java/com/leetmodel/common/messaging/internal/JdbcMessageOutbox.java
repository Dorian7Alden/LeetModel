package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PendingMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public String enqueue(String logicalTopic, String tag, MessageEnvelopeV1<?> envelope) {
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
        Instant now = Instant.now(clock);
        Instant leaseExpiresAt = now.plus(lease);
        List<String> candidates = jdbcTemplate.queryForList("""
                SELECT event_id
                FROM message_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                   OR (status = 'SENDING' AND lease_expires_at < ?)
                ORDER BY create_time
                LIMIT ?
                """, String.class, Timestamp.from(now), Timestamp.from(now), limit * 2);

        List<PendingMessage> claimed = new ArrayList<>();
        for (String eventId : candidates) {
            int changed = jdbcTemplate.update("""
                    UPDATE message_outbox
                    SET status = 'SENDING', lease_owner = ?, lease_expires_at = ?, update_time = ?
                    WHERE event_id = ?
                      AND ((status = 'PENDING' AND next_attempt_at <= ?)
                        OR (status = 'SENDING' AND lease_expires_at < ?))
                    """,
                    owner,
                    Timestamp.from(leaseExpiresAt),
                    Timestamp.from(now),
                    eventId,
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
            if (changed == 1) {
                claimed.add(findClaimed(eventId, owner));
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
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                SELECT MIN(create_time) FROM message_outbox WHERE status IN ('PENDING', 'SENDING')
                """, Timestamp.class);
        if (timestamp == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(timestamp.toInstant(), Instant.now(clock)).toSeconds());
    }

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

    private String requiredTag(String tag) {
        String value = Objects.requireNonNull(tag, "tag").trim();
        if (value.isEmpty() || !value.matches("[A-Z0-9_]{1,80}")) {
            throw new IllegalArgumentException("tag contains unsupported characters");
        }
        return value;
    }

    private String errorSummary(String error) {
        String value = error == null ? "Unknown" : error.replaceAll("[\\r\\n]+", " ");
        return value.substring(0, Math.min(value.length(), 500));
    }

    private void requireLease(int changed, String eventId) {
        if (changed != 1) {
            throw new IllegalStateException("outbox lease lost: " + eventId);
        }
    }
}
