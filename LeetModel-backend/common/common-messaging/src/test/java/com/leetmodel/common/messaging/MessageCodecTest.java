package com.leetmodel.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private static final String EVENT_ID = "00000000-0000-4000-8000-000000000001";

    private final MessageCodec codec = new MessageCodec(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            MessageCodec.MAX_PAYLOAD_BYTES
    );

    @Test
    void shouldRoundTripValidEnvelope() {
        MessageEnvelopeV1<Map<String, String>> envelope = envelope(Map.of("submissionId", "s-1"));

        byte[] body = codec.encode(envelope);
        MessageEnvelopeV1<?> decoded = codec.decode(body, Map.class);

        assertThat(decoded.eventId()).isEqualTo(EVENT_ID);
        assertThat(decoded.schemaVersion()).isEqualTo(MessageEnvelopeV1.VERSION);
        assertThat(decoded.payload()).isEqualTo(Map.of("submissionId", "s-1"));
    }

    @Test
    void shouldRejectUnsupportedSchemaAndOversizedBody() {
        MessageEnvelopeV1<Map<String, String>> unsupported = new MessageEnvelopeV1<>(
                EVENT_ID, "REVIEW_TASK_READY", 2, "submission-service", "submission", "s-1",
                "review:s-1:v1", Instant.parse("2026-09-01T00:00:00Z"), "trace-1", Map.of("id", "s-1")
        );
        MessageEnvelopeV1<Map<String, String>> oversized = envelope(
                Map.of("content", "x".repeat(MessageCodec.MAX_PAYLOAD_BYTES))
        );

        assertThatThrownBy(() -> codec.encode(unsupported))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("unsupported schemaVersion");
        assertThatThrownBy(() -> codec.encode(oversized))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void shouldRejectEventIdThatCannotFitInboxContract() {
        MessageEnvelopeV1<Map<String, String>> invalid = new MessageEnvelopeV1<>(
                "business-prefix-" + EVENT_ID,
                "REVIEW_TASK_READY",
                1,
                "submission-service",
                "submission",
                "s-1",
                "review:s-1:v1",
                Instant.parse("2026-09-01T00:00:00Z"),
                "trace-1",
                Map.of("id", "s-1")
        );

        assertThatThrownBy(() -> codec.encode(invalid))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("eventId");
    }

    private MessageEnvelopeV1<Map<String, String>> envelope(Map<String, String> payload) {
        return new MessageEnvelopeV1<>(
                EVENT_ID, "REVIEW_TASK_READY", 1, "submission-service", "submission", "s-1",
                "review:s-1:v1", Instant.parse("2026-09-01T00:00:00Z"), "trace-1", payload
        );
    }
}
