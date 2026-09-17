package com.leetmodel.ranking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** 全局排行视图中的单题业务统计。 */
@Data
@Builder
public class ProblemRankingStatsVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Integer problemCode;
    private String problemTitle;
    private Long submissionCount;
    private Long reviewedSubmissionCount;
    private Long rankedTeamCount;
    private BigDecimal averageScore;
    private BigDecimal highestScore;
}
