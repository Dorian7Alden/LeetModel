package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

/**
 * 最终提交锁定或变化事件的最小载荷。
 *
 * @param teamId 队伍标识
 * @param problemId 题目标识
 * @param submissionId 最终提交标识
 * @param lockedAt 锁定时间
 */
public record FinalSubmissionChangedPayload(
        @NotNull @Positive Long teamId,
        @NotNull @Positive Long problemId,
        @NotNull @Positive Long submissionId,
        @NotNull LocalDateTime lockedAt
) {
}
