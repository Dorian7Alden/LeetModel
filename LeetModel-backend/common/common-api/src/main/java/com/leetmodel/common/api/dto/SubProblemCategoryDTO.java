package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 数模小问题型特征与考点标准契约。
 * 作为下游任务派发、知识库精准召回与动态上下文切片的唯一基准输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubProblemCategoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 小问编号 (从 1 递增，例如 1 代表第 1 小问，0 代表全篇综合/摘要)
     */
    private Integer questionNo;

    /**
     * 题型枚举代码 (大写)
     * 对应: OPTIMIZATION, MECHANISM, PREDICTION, EVALUATION,
     *       DATA_MINING_STATISTICS, GRAPH_NETWORK, GENERAL_MODELING
     */
    private String categoryCode;

    /**
     * 题型中文名 (例如: "运筹优化类")
     */
    private String categoryName;

    /**
     * 核心考点与关注要素清单
     */
    private List<String> focusAspects;

    /**
     * 核心解法关键词 (用于知识检索与模型审查)
     */
    private List<String> typicalMethods;

    /**
     * 期望捕获的证据资产类型
     */
    private List<String> expectedEvidenceTypes;

    /**
     * 知识库检索场景代码与检索模板
     */
    private String retrievalScene;

    /**
     * 常见扣分硬伤警告 (踩坑防范点)
     */
    private List<String> commonPitfalls;
}
