package com.leetmodel.common.api.dto;

/**
 * 后台评价运行槽位就绪消息；只携带定位字段，不携带样本或模型正文。
 *
 * @param evaluationTaskId 评价任务标识
 * @param runAttemptId 物理运行 attempt 标识
 * @param slotKey 逻辑槽位稳定键
 * @param attemptNo 物理尝试序号
 * @param featureCode 功能标识
 * @param datasetVersion 不可变数据集版本
 */
public record EvaluationSlotReadyPayload(
        Long evaluationTaskId,
        Long runAttemptId,
        String slotKey,
        Integer attemptNo,
        String featureCode,
        String datasetVersion
) {
}
