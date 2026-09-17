package com.leetmodel.evaluation.model;

/** 原始指标缺失时允许保留的语义；不存在填零策略。 */
public enum NormalizationMissingPolicy {
    MARK_UNAVAILABLE,
    MARK_NOT_EVALUATED
}
