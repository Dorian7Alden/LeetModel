package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageOutbox;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.messaging.EvaluationSlotReadyMessageService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationOutboxTransactionTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private EvaluationSlotReadyMessageService messages;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:evaluation-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE evaluation_run_attempt(id BIGINT PRIMARY KEY, task_id BIGINT NOT NULL)");
        jdbc.execute("""
                CREATE TABLE message_outbox (
                  event_id VARCHAR(36) PRIMARY KEY, topic VARCHAR(255), tag VARCHAR(80),
                  message_key VARCHAR(255), event_type VARCHAR(100), schema_version INT,
                  source_service VARCHAR(100), aggregate_type VARCHAR(100), aggregate_id VARCHAR(100),
                  idempotency_key VARCHAR(255), trace_id VARCHAR(100), payload_json TEXT,
                  status VARCHAR(20), retry_count INT, next_attempt_at TIMESTAMP,
                  lease_owner VARCHAR(160), lease_expires_at TIMESTAMP, broker_message_id VARCHAR(255),
                  last_error VARCHAR(500), occurred_at TIMESTAMP, published_at TIMESTAMP,
                  create_time TIMESTAMP, update_time TIMESTAMP,
                  UNIQUE(event_type, idempotency_key))
                """);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        messages = new EvaluationSlotReadyMessageService(
                new MessageEnvelopeFactory("ai-evaluation-service", clock),
                new JdbcMessageOutbox(jdbc,
                        new MessageCodec(mapper, MessageCodec.MAX_PAYLOAD_BYTES),
                        new MessagingNamespace("lm-test"), clock));
    }

    @Test
    void commitSlotFactAndReadyOutboxTogether() {
        transaction.executeWithoutResult(status -> createSlotAndMessage());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM message_outbox", String.class))
                .isEqualTo("EVALUATION_SLOT_READY");
    }

    @Test
    void rollbackSlotFactAndReadyOutboxTogether() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            createSlotAndMessage();
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class)).isZero();
    }

    private void createSlotAndMessage() {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        task.setTraceId("trace-evaluation-20");
        task.setFeatureCode("REVIEW");
        task.setDatasetVersion("REVIEW_DATASET_V1");
        EvaluationRunAttempt run = new EvaluationRunAttempt();
        run.setId(301L);
        run.setTaskId(20L);
        run.setSlotKey("20:101:1");
        run.setAttemptNo(1);
        jdbc.update("INSERT INTO evaluation_run_attempt(id, task_id) VALUES (?, ?)", 301L, 20L);
        messages.enqueue(task, run, 0L);
    }
}
