 package com.leetmodel.common.api.vo;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.time.LocalDateTime;
 
 /**
  * 管理员视角提交记录视图对象。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class SubmissionAdminVO {
 
     @JsonSerialize(using = ToStringSerializer.class)
     private Long id;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long teamId;
     private String teamName;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long problemId;
     private Integer problemCode;
     private String problemTitle;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submitterId;
    private String submitterName;
    private String submitterAvatarUrl;
    private Integer version;
     private String originalFilename;
     @JsonSerialize(using = ToStringSerializer.class)
     private Long fileId;
     private Long fileSize;
     private String status;
     private Boolean finalVersion;
     private LocalDateTime createTime;
 }
