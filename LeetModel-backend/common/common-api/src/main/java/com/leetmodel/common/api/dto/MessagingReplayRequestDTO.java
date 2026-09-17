package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 人工补发命令；批量上限固定为 20，防止无界重放。 */
public record MessagingReplayRequestDTO(
        @NotEmpty @Size(max = 20) List<@Size(max = 36) String> eventIds,
        @Size(min = 3, max = 200) String reason
) {
}
