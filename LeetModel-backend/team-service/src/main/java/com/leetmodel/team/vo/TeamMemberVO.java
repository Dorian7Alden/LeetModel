package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 团队成员 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
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
