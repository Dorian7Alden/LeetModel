package com.leetmodel.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目响应 VO（列表和详情共用）。
 * <p>
 * 列表场景：不含 links；详情场景：含 links。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemVO {

    /** 题目 ID */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 标签名称列表 */
    private List<String> tagNames;

    /** 外部链接列表（仅详情接口返回） */
    private List<LinkVO> links;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkVO {
        private Long id;
        private String title;
        private String url;
        private String description;
        private Integer sortOrder;
    }
}
