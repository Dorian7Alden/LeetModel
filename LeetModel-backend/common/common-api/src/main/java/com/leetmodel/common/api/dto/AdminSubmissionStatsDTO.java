 package com.leetmodel.common.api.dto;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.util.List;
 
 /**
  * 管理端论文提交统计大盘指标 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminSubmissionStatsDTO {
     private Long totalSubmissions;
     private Long successSubmissions;
     private Long failedSubmissions;
     private Long processingSubmissions;
     private Long finalSubmissions;
     private Long todaySubmissions;
     private List<TopProblemSubmissionStats> topProblems;
 
     /**
      * 热门赛题提交统计。
      */
     @Data
     @Builder
     @NoArgsConstructor
     @AllArgsConstructor
     public static class TopProblemSubmissionStats {
         @JsonSerialize(using = ToStringSerializer.class)
         private Long problemId;
         private Integer problemCode;
         private String problemTitle;
         private Long submissionCount;
     }
 }
