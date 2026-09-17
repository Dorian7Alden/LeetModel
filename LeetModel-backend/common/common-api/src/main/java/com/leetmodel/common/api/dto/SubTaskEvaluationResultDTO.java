package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段二单任务执行结果契约。
 * 由各个并发 Worker 产出，必须具备完全的可合并性与自洽性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskEvaluationResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String taskType;
    private Integer targetQuestionNo;
    private String executionStatus;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String evaluationSummary;
    private List<SubTaskAspectScoreDTO> aspectScores;
    private List<SubTaskObservationDTO> observations;
    private List<SubTaskFindingDTO> findings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskAspectScoreDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String aspectCode;
        private String aspectName;
        private BigDecimal maxScore;
        private BigDecimal score;
        private String deductionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskObservationDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String observationId;
        private String blockId;
        private Integer physicalPage;
        private String observationType;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskFindingDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String findingId;
        private String taskId;
        private Integer questionNo;
        private String type;
        private String severity;
        private String statement;
        private String scoreImpact;
        private String blockId;
        private Integer physicalPage;
        private List<String> observationIds;
    }
}
