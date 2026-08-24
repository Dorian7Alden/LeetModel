package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队详情 VO（含成员列表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String description;
    private Long leaderId;
    private Long problemId;
    private Integer maxMembers;
    private Integer status;
    private String practiceStatus;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime endedAt;
    private Boolean recruiting;
    private Boolean needModeler;
    private Boolean needProgrammer;
    private Boolean needWriter;
    private Integer memberCount;
    private Integer remainingSlots;
    private String currentUserRelation;
    private Boolean canApply;
    private Boolean canManage;
    private Boolean canLeave;
    private LocalDateTime createTime;

    /** 团队成员列表 */
    private List<TeamMemberVO> members;
}
