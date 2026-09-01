package com.leetmodel.common.api.dto;

import java.time.LocalDateTime;

/** Broker 死信队列摘要；不包含消息正文和连接配置。 */
public record MessagingDeadLetterQueueDTO(
        String service,
        String consumerGroup,
        String topic,
        long messageCount,
        LocalDateTime oldestMessageAt,
        boolean available
) {
}
