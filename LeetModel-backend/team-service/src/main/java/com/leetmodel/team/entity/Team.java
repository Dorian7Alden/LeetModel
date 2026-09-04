package com.leetmodel.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team")
public class Team extends BaseEntity {

    /** 团队名称 */
    private String name;

    /** 团队描述 */
    private String description;

    /** 队长用户 ID */
    private Long leaderId;

    private Long problemId;

    /** 状态：1=活跃 0=已解散 */
    private Integer status;

    private String practiceStatus;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime deadlineAt;
    private java.time.LocalDateTime endedAt;

}
