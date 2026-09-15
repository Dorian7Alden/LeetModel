 package com.leetmodel.common.api.vo;
 
 import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 import java.time.LocalDateTime;
 import java.util.List;
 
 /**
  * 管理员视角队伍视图对象。
  */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 public class TeamAdminVO {
 
     @JsonSerialize(using = ToStringSerializer.class)
     private Long id;
     private String name;
     private String description;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderId;
    private String leaderNickname;
    private String leaderAvatarUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
     private Integer problemCode;
     private String problemTitle;
     private Integer maxMembers;
     private Integer memberCount;
     private Integer status;
     private String practiceStatus;
     private LocalDateTime startedAt;
     private LocalDateTime deadlineAt;
     private LocalDateTime endedAt;
     private LocalDateTime createTime;
     private List<MemberSimpleVO> members;
 
     /**
      * 队伍成员简要信息。
      */
     @Data
     @Builder
     @NoArgsConstructor
     @AllArgsConstructor
     public static class MemberSimpleVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private String role;
        private Boolean modeler;
         private Boolean programmer;
         private Boolean writer;
         private Boolean canSubmit;
         private LocalDateTime joinedAt;
     }
 }
