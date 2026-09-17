package com.leetmodel.common.api.dto;

import java.util.List;
import java.util.Map;

/** 单个服务的可靠消息运维概览。 */
public record MessagingOverviewDTO(
        String service,
        Map<String, Long> outbox,
        long inboxConsumed,
        long oldestPendingSeconds,
        List<MessagingConsumerDTO> consumers,
        Map<String, Long> domainBacklog,
        String replayMode
) {
}
