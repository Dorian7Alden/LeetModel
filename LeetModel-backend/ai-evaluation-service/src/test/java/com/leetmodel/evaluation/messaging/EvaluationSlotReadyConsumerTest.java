package com.leetmodel.evaluation.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.EvaluationSlotReadyPayload;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.service.EvaluationWorkerCoordinator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EvaluationSlotReadyConsumerTest {

    @Test
    void duplicateDeliveryWritesInboxOnceButAlwaysReissuesLocalWakeup() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:evaluation-inbox;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_inbox (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, consumer_group VARCHAR(255) NOT NULL,
                  event_id VARCHAR(36) NOT NULL, event_type VARCHAR(100) NOT NULL,
                  source_service VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
                  occurred_at TIMESTAMP NOT NULL, consumed_at TIMESTAMP,
                  create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
                  UNIQUE(consumer_group, event_id))
                """);
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        JdbcMessageInbox inbox = new JdbcMessageInbox(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new MessagingNamespace("lm-test"), Clock.systemUTC());
        EvaluationRunAttemptMapper mapper = mock(EvaluationRunAttemptMapper.class);
        EvaluationWorkerCoordinator coordinator = mock(EvaluationWorkerCoordinator.class);
        EvaluationSlotReadyConsumer consumer = new EvaluationSlotReadyConsumer(
                codec, inbox, mapper, coordinator);
        MessageEnvelopeV1<EvaluationSlotReadyPayload> envelope = new MessageEnvelopeV1<>(
                UUID.randomUUID().toString(), EvaluationSlotMessageContract.EVENT_TYPE, 1,
                "ai-evaluation-service", "evaluation-slot", "301",
                "evaluation-slot:301:attempt:1:wakeup:0", Instant.now(),
                UUID.randomUUID().toString(), new EvaluationSlotReadyPayload(
                20L, 301L, "20:101:1", 1, "REVIEW", "REVIEW_DATASET_V1"));
        byte[] body = codec.encode(envelope);

        consumer.onMessage(body);
        consumer.onMessage(body);

        verify(mapper, times(1)).markWakeup(eq(301L), eq(20L), eq(1), any());
        verify(coordinator, times(2)).wakeup(301L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_inbox", Long.class)).isEqualTo(1);
    }
}
