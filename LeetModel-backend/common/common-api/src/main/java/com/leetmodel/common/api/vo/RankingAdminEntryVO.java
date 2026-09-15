 package com.leetmodel.common.api.vo;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.math.BigDecimal;
 import java.time.LocalDateTime;
 
 /**
  * 管理端榜单条目明细 VO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class RankingAdminEntryVO {
 
     @JsonSerialize(using = ToStringSerializer.class)
     private Long id;
     private Integer rank;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long problemId;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long teamId;
     private String teamName;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long submissionId;
     private BigDecimal score;
     private String workflowVersion;
     private LocalDateTime submittedAt;
     private LocalDateTime reviewFinishedAt;
 }
