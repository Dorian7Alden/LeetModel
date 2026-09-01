package com.leetmodel.common.api.dto;

import java.time.LocalDateTime;

/** Inbox 消费事实，用于按 eventId/traceId 追踪和重复消费核验。 */
public record MessagingInboxRecordDTO(
        String service,
        String consumerGroup,
        String eventId,
        String eventType,
        String sourceService,
        String traceId,
        String status,
        LocalDateTime occurredAt,
        LocalDateTime consumedAt,
        LocalDateTime updatedAt
) {
}
