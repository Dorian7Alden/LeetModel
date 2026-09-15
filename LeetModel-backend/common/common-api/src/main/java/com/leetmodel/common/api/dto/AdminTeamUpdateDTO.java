 package com.leetmodel.common.api.dto;
 
 import jakarta.validation.constraints.Size;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 /**
  * 管理端修改队伍基础信息 DTO。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class AdminTeamUpdateDTO {
 
     @Size(max = 64, message = "队伍名称最多64个字符")
     private String name;
 
     @Size(max = 256, message = "队伍描述最多256个字符")
     private String description;
 
     private Integer maxMembers;
 
     private Long leaderId;
 }
