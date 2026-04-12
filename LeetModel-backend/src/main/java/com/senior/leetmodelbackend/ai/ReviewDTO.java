package com.senior.leetmodelbackend.ai;

import java.util.List;

/**
 * 评审记录主类
 */
public record ReviewDTO(
        BasicInfo basicInfo,
        List<DimensionDetail> dimensionDetails,
        Double weightedTotalScore
) {

    /**
     * 基本信息
     */
    public record BasicInfo(
            String modelingProblemTitle,
            String scoringDate,
            Integer maxScore
    ) {}

    /**
     * 维度详情
     */
    public record DimensionDetail(
            Integer dimensionIndex,
            String dimensionName,
            Double weight,
            Integer dimensionScore,
            String scoringReason
    ) {}
}
