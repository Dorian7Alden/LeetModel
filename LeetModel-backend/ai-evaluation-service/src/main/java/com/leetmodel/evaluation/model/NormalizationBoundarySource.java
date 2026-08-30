package com.leetmodel.evaluation.model;

/** 归一化固定边界的可追溯来源；当前批次统计量不属于合法来源。 */
public enum NormalizationBoundarySource {
    BUSINESS_THRESHOLD,
    VERSIONED_BASELINE,
    NOT_APPLICABLE
}
