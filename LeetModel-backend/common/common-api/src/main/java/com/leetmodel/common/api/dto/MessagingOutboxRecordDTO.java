package com.leetmodel.common.api.dto;

import java.time.LocalDateTime;

/** Outbox 脱敏运维记录；明确不返回 payload、幂等键和 Broker 地址。 */
public record MessagingOutboxRecordDTO(
        String service,
        String eventId,
        String topic,
        String tag,
        String eventType,
        String aggregateType,
        String aggregateId,
        String traceId,
        String status,
        int retryCount,
        String lastError,
        LocalDateTime occurredAt,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt
) {
}
