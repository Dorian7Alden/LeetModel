 package com.leetmodel.common.api.dto;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.math.BigDecimal;
 import java.util.List;
 
 /**
  * 赛题榜单深度统计指标与奖项预测 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminProblemRankingDetailStatsDTO {
 
     @JsonSerialize(using = ToStringSerializer.class)
     private Long problemId;
     private Integer problemCode;
     private String problemTitle;
     private Long totalTeams;
     private BigDecimal averageScore;
     private BigDecimal medianScore;
     private BigDecimal highestScore;
     private BigDecimal lowestScore;
     private BigDecimal stdDevScore;
     private BigDecimal nationalFirstTierScore;
     private BigDecimal nationalSecondTierScore;
     private BigDecimal provincialFirstTierScore;
     private List<ScoreBucket> scoreDistribution;
 
     /**
      * 分数段计数。
      */
     @Data
     @Builder
     @NoArgsConstructor
     @AllArgsConstructor
     public static class ScoreBucket {
         private String range;
         private Long count;
     }
 }
