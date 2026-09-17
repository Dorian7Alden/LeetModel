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
    SCALE_LIMIT_EXCEEDED(41108, "评价批次规模超过服务端限制"),
    DUPLICATE_CANDIDATE(41109, "评价批次不能包含重复候选版本"),
    TASK_STATE_CONFLICT(41110, "评价任务当前状态不允许该操作"),
    WEIGHT_SCHEME_INVALID(41111, "权重方案配置不兼容"),
    WEIGHT_SCHEME_VERSION_DUPLICATE(41112, "权重方案版本已存在"),
    WEIGHT_SCHEME_NOT_FOUND(41113, "权重方案不存在"),
    SCORE_RECALCULATION_NOT_ALLOWED(41114, "当前评价数据不满足重新计算条件"),
    DEPENDENCY_UNAVAILABLE(51101, "质量评价依赖服务暂不可用");

    private final int code;
    private final String message;
}
