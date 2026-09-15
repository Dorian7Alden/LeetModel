 package com.leetmodel.common.api.dto;
 
 import jakarta.validation.constraints.Max;
 import jakarta.validation.constraints.Min;
 import lombok.AllArgsConstructor;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 /**
  * 管理端提交记录分页检索请求。
  */
 @Data
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminSubmissionPageQuery {
 
     @Min(value = 1, message = "页码最小为1")
     private int page = 1;
 
     @Min(value = 1, message = "每页最少1条")
     @Max(value = 100, message = "每页最多100条")
     private int pageSize = 20;
 
     private String keyword;
     private Long teamId;
     private Long problemId;
     private Long submitterId;
     private String status;
     private Boolean finalOnly;
 }
