package com.leetmodel.common.messaging;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 为生产者创建字段一致的消息信封。
 */
public final class MessageEnvelopeFactory {

    private final String sourceService;
    private final Clock clock;

    /**
     * 创建信封工厂。
     *
     * @param sourceService 当前服务名
     * @param clock 时间源
     */
    public MessageEnvelopeFactory(String sourceService, Clock clock) {
        this.sourceService = Objects.requireNonNull(sourceService, "sourceService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 创建第一版消息信封。
     *
     * @param eventType 事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @param idempotencyKey 业务幂等键
     * @param traceId 调用链标识
     * @param payload 最小载荷
     * @param <T> 载荷类型
     * @return 新消息信封
     */
    public <T> MessageEnvelopeV1<T> create(
            String eventType,
            String aggregateType,
            String aggregateId,
            String idempotencyKey,
            String traceId,
            T payload
    ) {
        return new MessageEnvelopeV1<>(
                UUID.randomUUID().toString(),
                eventType,
                MessageEnvelopeV1.VERSION,
                sourceService,
                aggregateType,
                aggregateId,
                idempotencyKey,
                Instant.now(clock),
                traceId,
                CorrelationContext.operationId(),
                payload
        );
    }
}
