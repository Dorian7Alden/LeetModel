package com.leetmodel.team.vo;

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

    private Long id;
    private Long userId;
    private String role;
    private Boolean modeler;
    private Boolean programmer;
    private Boolean writer;
    private LocalDateTime joinedAt;
}
