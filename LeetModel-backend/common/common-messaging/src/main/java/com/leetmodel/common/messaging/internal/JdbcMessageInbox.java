package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.common.messaging.MessagingNamespace;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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
                            (consumer_group, event_id, event_type, source_service, status,
                             occurred_at, create_time, update_time)
                        VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?, ?)
                        """,
                        namespace.consumerGroup(logicalConsumerGroup),
                        envelope.eventId(),
                        envelope.eventType(),
                        envelope.sourceService(),
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
}
