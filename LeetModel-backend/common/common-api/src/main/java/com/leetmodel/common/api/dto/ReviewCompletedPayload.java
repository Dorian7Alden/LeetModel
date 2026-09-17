package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

/**
 * 正式评审完成事件的最小载荷，不复制分数或结果正文。
 *
 * @param reviewTaskId 评审任务标识
 * @param submissionId 提交标识
 * @param teamId 队伍标识
 * @param problemId 题目标识
 * @param workflowVersion 评审工作流版本
 * @param finishedAt 完成时间
 */
public record ReviewCompletedPayload(
        @NotNull @Positive Long reviewTaskId,
        @NotNull @Positive Long submissionId,
        @NotNull @Positive Long teamId,
        @NotNull @Positive Long problemId,
        @NotBlank String workflowVersion,
        @NotNull LocalDateTime finishedAt
) {
}
