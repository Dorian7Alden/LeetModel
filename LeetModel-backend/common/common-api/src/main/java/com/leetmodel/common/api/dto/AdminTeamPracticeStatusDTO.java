 package com.leetmodel.common.api.dto;
 
 import jakarta.validation.constraints.NotBlank;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.time.LocalDateTime;
 
 /**
  * 管理端调整队伍练习阶段与截止时间 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminTeamPracticeStatusDTO {
 
     @NotBlank(message = "练习状态不能为空")
     private String practiceStatus;
 
     private LocalDateTime deadlineAt;
 }
