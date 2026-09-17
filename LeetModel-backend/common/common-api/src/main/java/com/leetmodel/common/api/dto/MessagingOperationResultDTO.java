package com.leetmodel.common.api.dto;

import java.util.List;

/** 消息运维命令结果。 */
public record MessagingOperationResultDTO(
        String service,
        String operation,
        int affected,
        List<String> acceptedIds
) {
}
