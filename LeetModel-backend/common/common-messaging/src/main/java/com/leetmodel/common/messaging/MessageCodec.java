package com.leetmodel.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 负责消息信封校验和 JSON 编解码。
 */
public final class MessageCodec {

    /** 项目级消息体硬上限。 */
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile(
            "(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})"
                    + "|(?:[0-9A-HJKMNP-TV-Z]{26})"
    );
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

    private final ObjectMapper objectMapper;
    private final int maxPayloadBytes;

    /**
     * 创建消息编解码器。
     *
     * @param objectMapper JSON 映射器
     * @param maxPayloadBytes 最大消息字节数
     */
    public MessageCodec(ObjectMapper objectMapper, int maxPayloadBytes) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxPayloadBytes < 1024 || maxPayloadBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes must be between 1024 and 65536");
        }
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * 校验并序列化信封。
     *
     * @param envelope 消息信封
     * @return UTF-8 JSON
     */
    public byte[] encode(MessageEnvelopeV1<?> envelope) {
        validate(envelope);
        try {
            byte[] body = objectMapper.writeValueAsBytes(envelope);
            if (body.length > maxPayloadBytes) {
                throw new MessageContractException("message exceeds " + maxPayloadBytes + " bytes");
            }
            return body;
        } catch (JsonProcessingException exception) {
            throw new MessageContractException("message cannot be serialized", exception);
        }
    }

    /**
     * 反序列化并校验信封。
     *
     * @param body UTF-8 JSON
     * @param payloadType 载荷类型
     * @param <T> 载荷类型
     * @return 消息信封
     */
    public <T> MessageEnvelopeV1<T> decode(byte[] body, Class<T> payloadType) {
        if (body == null || body.length == 0 || body.length > maxPayloadBytes) {
            throw new MessageContractException("message body size is invalid");
        }
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(MessageEnvelopeV1.class, payloadType);
            MessageEnvelopeV1<T> envelope = objectMapper.readValue(body, type);
            validate(envelope);
            return envelope;
        } catch (IOException exception) {
            throw new MessageContractException("message cannot be deserialized", exception);
        }
    }

    /**
     * 将 JSON 文本转换为 UTF-8 字节。
     *
     * @param json JSON 文本
     * @return 字节内容
     */
    public byte[] bytes(String json) {
        byte[] body = Objects.requireNonNull(json, "json").getBytes(StandardCharsets.UTF_8);
        if (body.length > maxPayloadBytes) {
            throw new MessageContractException("message exceeds " + maxPayloadBytes + " bytes");
        }
        return body;
    }

    private void validate(MessageEnvelopeV1<?> envelope) {
        if (envelope == null) throw new MessageContractException("message envelope is required");
        requireText(envelope.eventId(), "eventId", 36);
        requireText(envelope.eventType(), "eventType", 100);
        requireText(envelope.sourceService(), "sourceService", 100);
        requireText(envelope.aggregateType(), "aggregateType", 100);
        requireText(envelope.aggregateId(), "aggregateId", 100);
        requireText(envelope.idempotencyKey(), "idempotencyKey", 255);
        requireText(envelope.traceId(), "traceId", 100);
        optionalText(envelope.operationId(), "operationId", 100);
        if (!EVENT_ID_PATTERN.matcher(envelope.eventId()).matches()) {
            throw new MessageContractException("eventId must be UUID or ULID");
        }
        if (!EVENT_TYPE_PATTERN.matcher(envelope.eventType()).matches()) {
            throw new MessageContractException("eventType contains unsupported characters");
        }
        if (envelope.schemaVersion() != MessageEnvelopeV1.VERSION) {
            throw new MessageContractException("unsupported schemaVersion: " + envelope.schemaVersion());
        }
        if (envelope.occurredAt() == null) throw new MessageContractException("occurredAt is required");
        if (envelope.payload() == null) throw new MessageContractException("payload is required");
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new MessageContractException(field + " is required");
        if (value.length() > maxLength) throw new MessageContractException(field + " is too long");
    }

    private void optionalText(String value, String field, int maxLength) {
        if (value != null && (value.isBlank() || value.length() > maxLength
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"))) {
            throw new MessageContractException(field + " contains unsupported characters or length");
        }
    }
}
