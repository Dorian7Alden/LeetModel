 package com.leetmodel.common.api.dto;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.util.List;
 import java.util.Map;
 
 /**
  * 管理端队伍统计大盘指标 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminTeamStatsDTO {
     private Long totalTeams;
     private Long activeTeams;
     private Long disbandedTeams;
     private Long preparingTeams;
     private Long inProgressTeams;
     private Long endedTeams;
     private Map<Integer, Long> memberSizeDistribution;
     private List<TopProblemStats> topProblems;
 
     /**
      * 热门赛题队伍数统计项。
      */
     @Data
     @Builder
     @NoArgsConstructor
     @AllArgsConstructor
     public static class TopProblemStats {
         @JsonSerialize(using = ToStringSerializer.class)
         private Long problemId;
         private Integer problemCode;
         private String problemTitle;
         private Long teamCount;
     }
 }
