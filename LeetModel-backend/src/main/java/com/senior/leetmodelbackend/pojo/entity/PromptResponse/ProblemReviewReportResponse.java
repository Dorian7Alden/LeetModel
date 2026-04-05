package com.senior.leetmodelbackend.pojo.entity.PromptResponse;

import lombok.Data;
import java.util.List;

/**
 * 对应题目评分 Prompt 的 JSON 响应实体
 */
@Data
public class ProblemReviewReportResponse {

    private BasicInfo basicInfo;
    private List<DimensionDetail> dimensionDetails;
    private Double weightedTotalScore;

    @Data
    public static class BasicInfo {
        private String modelingProblemTitle;
        private String scoringDate;
        private Integer maxScore;
    }

    @Data
    public static class DimensionDetail {
        private Integer dimensionIndex;
        private String dimensionName;
        private Double weight;
        private Integer dimensionScore;
        private String scoringReason;
    }
}
