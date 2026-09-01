package com.leetmodel.common.api.dto;

/**
 * 建议任务就绪消息的最小载荷。
 *
 * @param taskId 建议任务标识
 * @param submissionId 提交标识
 * @param workflowVersion 建议工作流版本
 */
public record SuggestionTaskReadyPayload(
        Long taskId,
        Long submissionId,
        String workflowVersion
) {
}
