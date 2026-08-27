package com.leetmodel.evaluation.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EvaluationErrorCode implements ErrorCode {
    DATASET_NOT_FOUND(41101, "评价数据集不存在"),
    DUPLICATE_SAMPLE(41102, "评价数据集不能重复引用同一提交"),
    SAMPLE_UNAVAILABLE(41103, "评价样本不存在或信息不完整"),
    VERSION_UNAVAILABLE(41104, "评审版本不存在或不可执行"),
    TASK_NOT_FOUND(41105, "质量评价任务不存在"),
    TASK_NOT_FAILED(41106, "只有环境失败的质量评价任务可以重试"),
    IDEMPOTENCY_CONFLICT(41107, "请求标识已用于另一项质量评价"),
    DEPENDENCY_UNAVAILABLE(51101, "质量评价依赖服务暂不可用");

    private final int code;
    private final String message;
}
