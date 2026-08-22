package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目-标签关联实体。
 */
@Data
@TableName("problem_tag")
public class ProblemTag {

    /** 关联 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 题目 ID */
    private Long problemId;

    /** 标签 ID */
    private Long tagId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
