package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段一结构性静态规范评价结果契约。
 * 直接交付下游终态汇聚器消费，与阶段二并发结果进行矩阵式映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phase1StructuralReviewResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 阶段一总得分 (满分 25.0，精确到小数点后一位)
     */
    private BigDecimal score;

    /**
     * 阶段一基准满分 (固定 25.0)
     */
    private BigDecimal maxScore;

    /**
     * 四大静态切面分项打分
     */
    private List<StructuralAspectScore> aspects;

    /**
     * 阶段一提取的静态客观 Findings 证据列表
     */
    private List<StructuralFindingDTO> findings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuralAspectScore implements Serializable {
        private static final long serialVersionUID = 1L;

        private String aspectCode;
        private String aspectName;
        private BigDecimal maxScore;
        private BigDecimal score;
        private String reason;
        private List<String> findingIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuralFindingDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String findingId;
        private String aspectCode;
        private String type;
        private String severity;
        private String statement;
        private String blockId;
        private Integer physicalPage;
    }
}
