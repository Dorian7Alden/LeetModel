package com.leetmodel.common.messaging;

import java.time.Instant;

/**
 * Relay 已领取的 Outbox 消息。
 *
 * @param eventId 事件标识
 * @param topic 物理 Topic
 * @param tag 消息 Tag
 * @param messageKey Broker 查询 Key
 * @param eventType 事件类型
 * @param payloadJson 消息 JSON
 * @param retryCount 已失败次数
 * @param occurredAt 事实发生时间
 */
public record PendingMessage(
        String eventId,
        String topic,
        String tag,
        String messageKey,
        String eventType,
        String payloadJson,
        int retryCount,
        Instant occurredAt
) {
}
