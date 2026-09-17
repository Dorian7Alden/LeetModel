package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题目收藏关联实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("problem_favorite")
public class ProblemFavorite {

    /** 收藏 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 题目 ID */
    private Long problemId;

    /** 收藏时刻 */
    private LocalDateTime createTime;
}
