package com.leetmodel.common.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.audit.OperationAuditContract;
import com.leetmodel.common.api.audit.OperationAuditContractException;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;

import java.util.Objects;

/** 操作审计专用消息编解码器，补充信封与载荷之间的不可变关系。 */
public final class OperationAuditMessageCodec {

    /** 审计 Topic 的唯一稳定 Tag/eventType。 */
    public static final String EVENT_TYPE = "OPERATION_AUDIT_RECORDED";
    /** 审计消息使用 operationId 作为聚合。 */
    public static final String AGGREGATE_TYPE = "operation_audit";

    private final MessageCodec delegate;
    private final int maxPayloadBytes;

    /**
     * 创建严格编解码器；未知信封或载荷字段不会被静默忽略。
     *
     * @param objectMapper 应用 JSON 映射器
     * @param maxPayloadBytes 当前环境消息上限，不能超过 64 KiB
     */
    public OperationAuditMessageCodec(ObjectMapper objectMapper, int maxPayloadBytes) {
        ObjectMapper strictMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.delegate = new MessageCodec(strictMapper, maxPayloadBytes);
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /** 根据已分配 auditEventId 的载荷建立公共信封。 */
    public MessageEnvelopeV1<OperationAuditPayloadV1> envelope(OperationAuditPayloadV1 payload) {
        OperationAuditContract.validate(payload);
        return new MessageEnvelopeV1<>(
                payload.auditEventId(),
                EVENT_TYPE,
                MessageEnvelopeV1.VERSION,
                payload.sourceService(),
                AGGREGATE_TYPE,
                payload.operationId(),
                payload.auditEventId(),
                payload.occurredAt(),
                payload.traceId(),
                payload.operationId(),
                payload
        );
    }

    /** 校验公共信封、操作审计载荷和跨层等式后编码。 */
    public byte[] encode(MessageEnvelopeV1<OperationAuditPayloadV1> envelope) {
        validate(envelope);
        byte[] body = delegate.encode(envelope);
        if (body.length >= maxPayloadBytes) {
            throw new MessageContractException("audit message must be smaller than " + maxPayloadBytes + " bytes");
        }
        return body;
    }

    /** 严格解码并再次校验审计契约。 */
    public MessageEnvelopeV1<OperationAuditPayloadV1> decode(byte[] body) {
        if (body != null && body.length >= maxPayloadBytes) {
            throw new MessageContractException("audit message body must be smaller than "
                    + maxPayloadBytes + " bytes");
        }
        MessageEnvelopeV1<OperationAuditPayloadV1> envelope = delegate.decode(
                body, OperationAuditPayloadV1.class);
        validate(envelope);
        return envelope;
    }

    private void validate(MessageEnvelopeV1<OperationAuditPayloadV1> envelope) {
        if (envelope == null) throw invalid("audit envelope is required");
        OperationAuditPayloadV1 payload = envelope.payload();
        OperationAuditContract.validate(payload);
        requireEqual(EVENT_TYPE, envelope.eventType(), "eventType");
        requireEqual(AGGREGATE_TYPE, envelope.aggregateType(), "aggregateType");
        requireEqual(payload.auditEventId(), envelope.eventId(), "auditEventId/eventId");
        requireEqual(payload.auditEventId(), envelope.idempotencyKey(), "auditEventId/idempotencyKey");
        requireEqual(payload.operationId(), envelope.aggregateId(), "operationId/aggregateId");
        requireEqual(payload.operationId(), envelope.operationId(), "operationId");
        requireEqual(payload.sourceService(), envelope.sourceService(), "sourceService");
        requireEqual(payload.occurredAt(), envelope.occurredAt(), "occurredAt");
        requireEqual(payload.traceId(), envelope.traceId(), "traceId");
    }

    private void requireEqual(Object expected, Object actual, String field) {
        if (!Objects.equals(expected, actual)) throw invalid(field + " does not match audit payload");
    }

    private OperationAuditContractException invalid(String message) {
        return new OperationAuditContractException(message);
    }
}
