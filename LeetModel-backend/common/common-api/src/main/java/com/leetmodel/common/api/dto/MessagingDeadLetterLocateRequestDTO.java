package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 单条或有界批量死信定位命令。 */
public record MessagingDeadLetterLocateRequestDTO(
        @NotBlank @Size(max = 255) String consumerGroup,
        @NotEmpty @Size(max = 20) List<@Size(max = 36) String> eventIds
) {
}
