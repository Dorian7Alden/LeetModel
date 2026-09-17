package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 管理端死信恢复命令；先在消费服务定位，再从源服务原始 Outbox 恢复。 */
public record MessagingDeadLetterReplayRequestDTO(
        @NotBlank @Size(max = 255) String consumerGroup,
        @NotEmpty @Size(max = 20) List<@Size(max = 36) String> eventIds,
        @NotBlank @Size(min = 3, max = 200) String reason
) {
}
