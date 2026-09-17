package com.leetmodel.evaluation.runner;

import lombok.Getter;

/** Runner 可分类失败，供核心决定完成槽位或保留恢复路径。 */
@Getter
public class EvaluationRunnerException extends RuntimeException {

    private final String failureType;

    public EvaluationRunnerException(String failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public EvaluationRunnerException(String failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }
}
