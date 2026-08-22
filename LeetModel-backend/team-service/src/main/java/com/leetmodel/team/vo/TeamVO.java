package com.leetmodel.team.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队详情 VO（含成员列表）。
 *
 * @author LeetModel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamVO {

    private Long id;
    private String name;
    private String description;
    private Long leaderId;
    private Integer maxMembers;
    private Integer status;
    private LocalDateTime createTime;

    /** 团队成员列表 */
    private List<TeamMemberVO> members;
}
