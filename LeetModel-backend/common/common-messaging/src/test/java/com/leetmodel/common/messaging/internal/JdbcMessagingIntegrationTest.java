package com.leetmodel.common.messaging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagingNamespace;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMessagingIntegrationTest {

    private static final String EVENT_ONE = "00000000-0000-4000-8000-000000000001";
    private static final String EVENT_RETRY = "00000000-0000-4000-8000-000000000002";
    private static final String EVENT_INBOX = "00000000-0000-4000-8000-000000000003";
    private static final String EVENT_ROLLBACK = "00000000-0000-4000-8000-000000000004";

    private JdbcTemplate jdbcTemplate;
    private MutableClock clock;
    private JdbcMessageOutbox outbox;
    private JdbcMessageInbox inbox;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("messaging-schema.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
        MessagingNamespace namespace = new MessagingNamespace("lm-test");
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                MessageCodec.MAX_PAYLOAD_BYTES
        );
        outbox = new JdbcMessageOutbox(jdbcTemplate, codec, namespace, clock);
        inbox = new JdbcMessageInbox(
                jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                namespace,
                clock
        );
    }

    @Test
    void shouldClaimWithLeaseAndRecoverExpiredOwner() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_ONE));

        assertThat(outbox.claim("relay-a", 10, Duration.ofSeconds(30)))
                .singleElement()
                .satisfies(message -> assertThat(message.topic()).isEqualTo("lm-test%review-task-v1"));
        assertThat(outbox.claim("relay-b", 10, Duration.ofSeconds(30))).isEmpty();

        clock.advance(Duration.ofSeconds(31));

        assertThat(outbox.claim("relay-b", 10, Duration.ofSeconds(30)))
                .extracting(message -> message.eventId())
                .containsExactly(EVENT_ONE);
        outbox.markPublished(EVENT_ONE, "relay-b", "broker-1");
        assertThat(outbox.count(OutboxStatus.PUBLISHED)).isEqualTo(1L);
    }

    @Test
    void shouldRenewOnlyForCurrentLeaseOwner() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_ONE));
        outbox.claim("relay-a", 10, Duration.ofSeconds(30));

        assertThat(outbox.renewLease(EVENT_ONE, "relay-a", Duration.ofSeconds(30))).isTrue();
        assertThat(outbox.renewLease(EVENT_ONE, "relay-b", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldScheduleRetryAndBlockPermanentFailure() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_RETRY));
        outbox.claim("relay-a", 10, Duration.ofSeconds(30));
        outbox.markRetry(EVENT_RETRY, "relay-a", clock.instant().plusSeconds(5), "timeout");

        assertThat(outbox.claim("relay-a", 10, Duration.ofSeconds(30))).isEmpty();
        clock.advance(Duration.ofSeconds(5));
        assertThat(outbox.claim("relay-a", 10, Duration.ofSeconds(30)))
                .singleElement()
                .satisfies(message -> assertThat(message.retryCount()).isEqualTo(1));

        outbox.markBlocked(EVENT_RETRY, "relay-a", "TOPIC_NOT_EXIST");
        assertThat(outbox.count(OutboxStatus.BLOCKED)).isEqualTo(1L);
    }

    @Test
    void shouldCommitInboxAndDomainActionExactlyOnce() {
        AtomicInteger actionCount = new AtomicInteger();
        MessageEnvelopeV1<Map<String, String>> envelope = envelope(EVENT_INBOX);

        InboxResult first = inbox.executeOnce("cg-ai-review-task-v1", envelope, () -> {
            jdbcTemplate.update("INSERT INTO domain_probe(id) VALUES (?)", "domain-1");
            actionCount.incrementAndGet();
        });
        InboxResult duplicate = inbox.executeOnce("cg-ai-review-task-v1", envelope,
                actionCount::incrementAndGet);

        assertThat(first).isEqualTo(InboxResult.CONSUMED);
        assertThat(duplicate).isEqualTo(InboxResult.DUPLICATE);
        assertThat(actionCount).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_probe", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void shouldRollbackInboxWhenDomainActionFails() {
        MessageEnvelopeV1<Map<String, String>> envelope = envelope(EVENT_ROLLBACK);

        assertThatThrownBy(() -> inbox.executeOnce("cg-ai-review-task-v1", envelope, () -> {
            jdbcTemplate.update("INSERT INTO domain_probe(id) VALUES (?)", "domain-rollback");
            throw new IllegalStateException("database dependency failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_inbox", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_probe", Long.class))
                .isZero();
        assertThat(inbox.executeOnce("cg-ai-review-task-v1", envelope, () -> {
        })).isEqualTo(InboxResult.CONSUMED);
    }

    @Test
    void shouldExposeSanitizedTraceFactsAndReplayPublishedEventWithSameId() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_ONE));
        outbox.claim("relay-a", 10, Duration.ofSeconds(30));
        outbox.markPublished(EVENT_ONE, "relay-a", "broker-secret-id");
        inbox.executeOnce("cg-ai-review-task-v1", envelope(EVENT_INBOX), () -> { });

        assertThat(outbox.findOperations("submission-service", "PUBLISHED", "trace-1", null, 10))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.eventId()).isEqualTo(EVENT_ONE);
                    assertThat(record.traceId()).isEqualTo("trace-1");
                    assertThat(record.toString()).doesNotContain("review:submission-1:v1")
                            .doesNotContain("broker-secret-id")
                            .doesNotContain("submissionId");
                });
        assertThat(inbox.findOperations("ai-review-service", "trace-1", null, 10))
                .singleElement()
                .satisfies(record -> assertThat(record.eventId()).isEqualTo(EVENT_INBOX));

        assertThat(outbox.replay(java.util.List.of(EVENT_ONE), "人工故障恢复"))
                .containsExactly(EVENT_ONE);
        assertThat(outbox.count(OutboxStatus.PENDING)).isEqualTo(1L);
        assertThat(outbox.claim("relay-b", 10, Duration.ofSeconds(30)))
                .extracting(message -> message.eventId())
                .containsExactly(EVENT_ONE);
    }

    private MessageEnvelopeV1<Map<String, String>> envelope(String eventId) {
        return new MessageEnvelopeV1<>(
                eventId,
                "REVIEW_TASK_READY",
                1,
                "submission-service",
                "submission",
                "submission-1",
                "review:submission-1:v1",
                clock.instant(),
                "trace-1",
                Map.of("submissionId", "submission-1")
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
