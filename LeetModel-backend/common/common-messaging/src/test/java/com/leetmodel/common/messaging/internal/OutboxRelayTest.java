package com.leetmodel.common.messaging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PermanentPublishException;
import com.leetmodel.common.messaging.PublishReceipt;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayTest {

    private static final String EVENT_SUCCESS = "00000000-0000-4000-8000-000000000005";
    private static final String EVENT_BLOCKED = "00000000-0000-4000-8000-000000000006";
    private static final String EVENT_RETRY = "00000000-0000-4000-8000-000000000007";

    private JdbcMessageOutbox outbox;
    private Clock clock;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("messaging-schema.sql")).execute(dataSource);
        clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        outbox = new JdbcMessageOutbox(
                new JdbcTemplate(dataSource),
                new MessageCodec(
                        new ObjectMapper().registerModule(new JavaTimeModule()),
                        MessageCodec.MAX_PAYLOAD_BYTES
                ),
                new MessagingNamespace("lm-test"),
                clock
        );
    }

    @Test
    void shouldPublishAndMarkOutbox() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_SUCCESS));
        MessagePublisher publisher = message -> new PublishReceipt("broker-1");

        relay(publisher).relayPending();

        assertThat(outbox.count(OutboxStatus.PUBLISHED)).isEqualTo(1L);
    }

    @Test
    void shouldBlockStableConfigurationError() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_BLOCKED));
        MessagePublisher publisher = message -> {
            throw new PermanentPublishException("missing topic", null);
        };

        relay(publisher).relayPending();

        assertThat(outbox.count(OutboxStatus.BLOCKED)).isEqualTo(1L);
    }

    @Test
    void shouldKeepTransientErrorPending() {
        outbox.enqueue("review-task-v1", "REVIEW_TASK_READY", envelope(EVENT_RETRY));
        MessagePublisher publisher = message -> {
            throw new IllegalStateException("broker timeout");
        };

        relay(publisher).relayPending();

        assertThat(outbox.count(OutboxStatus.PENDING)).isEqualTo(1L);
    }

    private OutboxRelay relay(MessagePublisher publisher) {
        return new OutboxRelay(
                outbox,
                publisher,
                new OutboxRetryPolicy(),
                new MessagingMetrics(null, outbox),
                clock,
                "relay-test",
                10,
                Duration.ofSeconds(30)
        );
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
}
