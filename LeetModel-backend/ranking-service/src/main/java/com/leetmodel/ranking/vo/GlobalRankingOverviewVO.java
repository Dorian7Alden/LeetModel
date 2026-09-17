package com.leetmodel.ranking.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 未选择具体题目时展示的全局排行统计。 */
@Data
@Builder
public class GlobalRankingOverviewVO {
    private Long totalSubmissions;
    private Long reviewedSubmissions;
    private Long rankedTeams;
    private Integer problemCount;
    private BigDecimal overallAverageScore;
    private LocalDateTime computedAt;
    private List<ProblemRankingStatsVO> items;
}
