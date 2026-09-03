package com.leetmodel.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.audit.OperationAuditContractException;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuditMessageCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OperationAuditMessageCodec codec = new OperationAuditMessageCodec(
            objectMapper, MessageCodec.MAX_PAYLOAD_BYTES);

    @Test
    void shouldBuildAndRoundTripStrictAuditEnvelope() {
        OperationAuditPayloadV1 payload = payload("approved-change", "after");

        MessageEnvelopeV1<OperationAuditPayloadV1> envelope = codec.envelope(payload);
        byte[] body = codec.encode(envelope);
        MessageEnvelopeV1<OperationAuditPayloadV1> decoded = codec.decode(body);

        assertThat(body.length).isLessThan(MessageCodec.MAX_PAYLOAD_BYTES);
        assertThat(decoded.eventId()).isEqualTo(payload.auditEventId());
        assertThat(decoded.eventId()).isEqualTo(decoded.payload().auditEventId());
        assertThat(decoded.eventType()).isEqualTo(OperationAuditResources.TAG);
        assertThat(decoded.operationId()).isEqualTo(payload.operationId());
        assertThat(decoded.aggregateType()).isEqualTo(OperationAuditMessageCodec.AGGREGATE_TYPE);
        assertThat(decoded.idempotencyKey()).isEqualTo(payload.auditEventId());
    }

    @Test
    void shouldRejectEnvelopePayloadIdentityMismatch() {
        MessageEnvelopeV1<OperationAuditPayloadV1> valid = codec.envelope(payload("approved-change", "after"));
        MessageEnvelopeV1<OperationAuditPayloadV1> mismatched = new MessageEnvelopeV1<>(
                "00000000-0000-4000-8000-000000000002",
                valid.eventType(),
                valid.schemaVersion(),
                valid.sourceService(),
                valid.aggregateType(),
                valid.aggregateId(),
                valid.idempotencyKey(),
                valid.occurredAt(),
                valid.traceId(),
                valid.operationId(),
                valid.payload()
        );

        assertThatThrownBy(() -> codec.encode(mismatched))
                .isInstanceOf(OperationAuditContractException.class)
                .hasMessageContaining("auditEventId/eventId");
    }

    @Test
    void shouldRejectUnknownEnvelopeAndPayloadFields() {
        String json = new String(codec.encode(codec.envelope(payload("approved-change", "after"))),
                StandardCharsets.UTF_8);
        String unknownEnvelope = json.replaceFirst("\\{", "{\"requestBody\":\"forbidden\",");
        String unknownPayload = json.replace("\"auditSchemaVersion\":1",
                "\"auditSchemaVersion\":1,\"prompt\":\"forbidden\"");

        assertThatThrownBy(() -> codec.decode(unknownEnvelope.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("deserialized");
        assertThatThrownBy(() -> codec.decode(unknownPayload.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("deserialized");
    }

    @Test
    void shouldEnforceConfiguredLimitWithoutCompression() {
        OperationAuditMessageCodec smallCodec = new OperationAuditMessageCodec(objectMapper, 1_024);
        OperationAuditPayloadV1 largeButFieldValid = payload("r".repeat(300), "v".repeat(256));

        assertThatThrownBy(() -> smallCodec.encode(smallCodec.envelope(largeButFieldValid)))
                .isInstanceOf(MessageContractException.class)
                .hasMessageContaining("1024 bytes");
    }

    @Test
    void shouldNotSerializeSensitiveOrGenericSnapshotFields() {
        String json = new String(codec.encode(codec.envelope(payload("approved-change", "after"))),
                StandardCharsets.UTF_8);

        assertThat(json).doesNotContain(
                "requestBody", "responseBody", "entitySnapshot", "passwordValue", "accessToken",
                "promptText", "answerText", "paperContent", "messagePayload"
        );
    }

    private OperationAuditPayloadV1 payload(String reason, String summaryValue) {
        return new OperationAuditPayloadV1(
                OperationAuditPayloadV1.VERSION,
                "00000000-0000-4000-8000-000000000001",
                "operation-1",
                "COMPLETED",
                Instant.parse("2026-09-03T00:00:00Z"),
                "user-service",
                "1.0.0",
                "USER_RBAC",
                "USER.ROLE_CHANGE",
                "HIGH",
                "SUCCEEDED",
                reason,
                null,
                "ADMIN",
                "admin-1",
                List.of("ROLE_ADMIN"),
                "USER",
                "user-1",
                "version-2",
                Map.of("roleCount", "1"),
                Map.of("roleCount", summaryValue),
                "trace-1",
                "sw-trace-1",
                "request-1",
                null,
                null,
                "a".repeat(64),
                "b".repeat(64)
        );
    }
}
