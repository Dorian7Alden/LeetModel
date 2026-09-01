package com.leetmodel.common.api.dto;

import java.util.List;

/** traceId 对应的消息生产、消费和 AI 调用事实。 */
public record MessagingTraceDTO(
        String traceId,
        List<MessagingOutboxRecordDTO> producedEvents,
        List<MessagingInboxRecordDTO> consumedEvents,
        List<AiCallLogDTO> aiCalls,
        List<String> unavailableServices
) {
}
