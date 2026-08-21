package com.senior.leetmodelbackend.pojo.enums;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum ReviewDimensionEnum {
    PROBLEM_DECONSTRUCTION("PROBLEM_DECONSTRUCTION", "问题拆解与假设合理性", new BigDecimal("0.15")),
    MODEL_BUILDING("MODEL_BUILDING", "模型构建与方法适配性", new BigDecimal("0.30")),
    SOLUTION_RELIABILITY("SOLUTION_RELIABILITY", "求解实现与结果可靠性", new BigDecimal("0.25")),
    RESULT_ANALYSIS("RESULT_ANALYSIS", "结果分析与模型拓展性", new BigDecimal("0.15")),
    DOCUMENT_STANDARD("DOCUMENT_STANDARD", "文档规范与逻辑完整性", new BigDecimal("0.15"));

    private final String code;
    private final String name;
    private final BigDecimal weight;

    ReviewDimensionEnum(String code, String name, BigDecimal weight) {
        this.code = code;
        this.name = name;
        this.weight = weight;
    }
}
