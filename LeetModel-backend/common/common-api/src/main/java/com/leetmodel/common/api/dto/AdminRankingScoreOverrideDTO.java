 package com.leetmodel.common.api.dto;
 
 import jakarta.validation.constraints.DecimalMax;
 import jakarta.validation.constraints.DecimalMin;
 import jakarta.validation.constraints.NotBlank;
 import jakarta.validation.constraints.NotNull;
 import jakarta.validation.constraints.Size;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.math.BigDecimal;
 
 /**
  * 管理端人工复议修正成绩 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminRankingScoreOverrideDTO {
 
     @NotNull(message = "修正成绩不能为空")
     @DecimalMin(value = "0.00", message = "成绩最低为0分")
     @DecimalMax(value = "100.00", message = "成绩最高为100分")
     private BigDecimal newScore;
 
     @NotBlank(message = "修正原因不能为空")
     @Size(max = 256, message = "修正原因最多256字符")
     private String reason;
 }
