 package com.leetmodel.common.api.dto;
 
 import jakarta.validation.constraints.NotBlank;
 import jakarta.validation.constraints.NotNull;
 import jakarta.validation.constraints.Positive;
 import jakarta.validation.constraints.Size;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 /**
  * 管理端代建队伍请求 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminTeamCreateDTO {
 
     @NotBlank(message = "队伍名称不能为空")
     @Size(max = 64, message = "队伍名称最多64个字符")
     private String name;
 
     @Size(max = 256, message = "队伍描述最多256个字符")
     private String description;
 
     @NotNull(message = "赛题ID不能为空")
     @Positive(message = "赛题ID必须为正数")
     private Long problemId;
 
     @NotNull(message = "队长用户ID不能为空")
     @Positive(message = "队长用户ID必须为正数")
     private Long leaderId;
 
     private Integer maxMembers;
 }
