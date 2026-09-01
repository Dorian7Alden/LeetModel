package com.leetmodel.common.messaging;

import java.time.Instant;

/**
 * 跨服务消息的第一版统一信封。
 *
 * @param eventId 全局事件标识
 * @param eventType 稳定事件类型
 * @param schemaVersion 契约版本
 * @param sourceService 事实所有者服务
 * @param aggregateType 聚合类型
 * @param aggregateId 聚合标识
 * @param idempotencyKey 业务幂等键
 * @param occurredAt 事实发生时间
 * @param traceId 调用链标识
 * @param operationId 可选治理操作标识
 * @param payload 最小事件载荷
 * @param <T> 载荷类型
 */
public record MessageEnvelopeV1<T>(
        String eventId,
        String eventType,
        int schemaVersion,
        String sourceService,
        String aggregateType,
        String aggregateId,
        String idempotencyKey,
        Instant occurredAt,
        String traceId,
        String operationId,
        T payload
) {

    /** 当前支持的契约版本。 */
    public static final int VERSION = 1;

    /**
     * 创建不属于人工治理操作的兼容信封。
     *
     * @param eventId 事件标识
     * @param eventType 事件类型
     * @param schemaVersion schema 版本
     * @param sourceService 来源服务
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @param idempotencyKey 业务幂等键
     * @param occurredAt 事实发生时间
     * @param traceId 业务关联标识
     * @param payload 最小载荷
     */
    public MessageEnvelopeV1(
            String eventId,
            String eventType,
            int schemaVersion,
            String sourceService,
            String aggregateType,
            String aggregateId,
            String idempotencyKey,
            Instant occurredAt,
            String traceId,
            T payload
    ) {
        this(eventId, eventType, schemaVersion, sourceService, aggregateType, aggregateId,
                idempotencyKey, occurredAt, traceId, null, payload);
    }
}
