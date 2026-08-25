package com.leetmodel.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 入队申请实体。
 */
@Data
@TableName("team_join_application")
public class TeamJoinApplication implements Serializable {

    private Long id;
    private Long teamId;
    private Long recruitmentId;
    private Long applicantId;
    private String message;
    private String status;
    private Integer pendingMarker;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createTime;
}
