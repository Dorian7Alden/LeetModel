package com.leetmodel.common.api.dto;

import java.time.LocalDateTime;

/** 按 eventId 定位到的死信元数据；正文、幂等键和业务载荷均不返回。 */
public record MessagingDeadLetterRecordDTO(
        String service,
        String consumerGroup,
        String eventId,
        String eventType,
        String sourceService,
        String brokerMessageId,
        int reconsumeTimes,
        LocalDateTime storedAt
) {
}
