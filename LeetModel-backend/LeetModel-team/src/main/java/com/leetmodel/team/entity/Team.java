package com.leetmodel.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队实体。
 *
 * @author LeetModel
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

    /** 最大成员数（默认 3） */
    private Integer maxMembers;

    /** 状态：1=活跃 0=已解散 */
    private Integer status;
}
