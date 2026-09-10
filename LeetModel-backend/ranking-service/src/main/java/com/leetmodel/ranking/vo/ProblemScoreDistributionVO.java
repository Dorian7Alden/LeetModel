package com.leetmodel.ranking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 指定题目当前榜单的匿名分数分布。 */
@Data
@Builder
public class ProblemScoreDistributionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    private List<ScoreBucketVO> buckets;

    @Data
    @Builder
    public static class ScoreBucketVO {
        private Integer score;
        private Long teamCount;
    }
}
