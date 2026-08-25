package com.leetmodel.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团队成员实体（不使用 BaseEntity，用联合唯一索引代替自增 ID）。
 */
@Data
@TableName("team_member")
public class TeamMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花算法） */
    private Long id;

    /** 团队 ID */
    private Long teamId;

    /** 用户 ID */
    private Long userId;

    /** 成员角色：leader / member */
    private String role;

    /** 是否为建模手 */
    private Boolean modeler;

    /** 是否为编程手 */
    private Boolean programmer;

    /** 是否为论文手 */
    private Boolean writer;

    /** 是否允许提交作品 */
    private Boolean canSubmit;

    /** 加入时间 */
    private LocalDateTime joinedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
