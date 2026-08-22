package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 题目实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("problem")
public class Problem extends BaseEntity {

    /** 题目标题 */
    private String title;

    /** 题目描述 MD 文件 ID */
    private Long contentFileId;

    /** 赛事类型：MCM_ICM / CUMCM */
    private String contestType;

    /** 难度：1=简单 2=中等 3=困难 */
    private Integer difficulty;

    /** 平均得分 */
    private BigDecimal averageScore;

    /** 状态：0=草稿 1=已发布 2=已下线 3=已归档 */
    private Integer status;

    /** 创建者用户 ID */
    private Long creatorId;
}
