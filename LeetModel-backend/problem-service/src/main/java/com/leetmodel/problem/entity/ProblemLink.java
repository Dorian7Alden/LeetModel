package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目外部链接实体（1:N 归属题目）。
 */
@Data
@TableName("problem_link")
public class ProblemLink {

    /** 链接 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 题目 ID */
    private Long problemId;

    /** 链接标题 */
    private String title;

    /** 链接地址 */
    private String url;

    /** 链接说明 */
    private String description;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 链接数据传输记录（Service 内部使用，不感知 DTO 类型）。
     */
    public record LinkData(String title, String url, String description, Integer sortOrder) {}
}
