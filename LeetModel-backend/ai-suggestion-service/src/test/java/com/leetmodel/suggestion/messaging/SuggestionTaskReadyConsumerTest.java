package com.leetmodel.suggestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.SuggestionTaskReadyPayload;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.service.SuggestionTaskWorkerCoordinator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SuggestionTaskReadyConsumerTest {

    @Test
    void duplicateDeliveryWritesInboxOnceButAlwaysReissuesLocalWakeup() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:suggestion-inbox;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_inbox (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, consumer_group VARCHAR(255) NOT NULL,
                  event_id VARCHAR(36) NOT NULL, event_type VARCHAR(100) NOT NULL,
                  source_service VARCHAR(100) NOT NULL, trace_id VARCHAR(100) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  occurred_at TIMESTAMP NOT NULL, consumed_at TIMESTAMP,
                  create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
                  UNIQUE(consumer_group, event_id))
                """);
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        JdbcMessageInbox inbox = new JdbcMessageInbox(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new MessagingNamespace("lm-test"), Clock.systemUTC());
        SuggestionTaskMapper mapper = mock(SuggestionTaskMapper.class);
        SuggestionTaskWorkerCoordinator coordinator = mock(SuggestionTaskWorkerCoordinator.class);
        SuggestionTaskReadyConsumer consumer = new SuggestionTaskReadyConsumer(
                codec, inbox, mapper, coordinator);
        MessageEnvelopeV1<SuggestionTaskReadyPayload> envelope = new MessageEnvelopeV1<>(
                UUID.randomUUID().toString(), SuggestionTaskMessageContract.EVENT_TYPE, 1,
                "ai-suggestion-service", "suggestion-task", "9001",
                "suggestion:9001:attempt:1:wakeup:0", Instant.now(), UUID.randomUUID().toString(),
                new SuggestionTaskReadyPayload(9001L, 101L, "GROUNDED_SUGGESTION_V2"));
        byte[] body = codec.encode(envelope);

        consumer.onMessage(body);
        consumer.onMessage(body);

        verify(mapper, times(1)).markWakeup(eq(9001L), eq(101L),
                eq("GROUNDED_SUGGESTION_V2"), any());
        verify(coordinator, times(2)).wakeup(9001L);
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM message_inbox", Long.class)).isEqualTo(1);
    }
}
