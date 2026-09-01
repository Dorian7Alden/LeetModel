package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 提交服务通知评审服务创建正式评审任务的最小载荷。
 *
 * @param submissionId 提交标识
 * @param teamId 队伍标识
 * @param problemId 题目标识
 * @param workflowVersion 锁定的评审工作流版本
 */
public record ReviewTaskReadyPayload(
        @NotNull @Positive Long submissionId,
        @NotNull @Positive Long teamId,
        @NotNull @Positive Long problemId,
        @NotBlank String workflowVersion
) {
}
