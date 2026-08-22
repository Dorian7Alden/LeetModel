package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新题目请求（所有字段可选，只更新非空字段）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemUpdateRequest {

    /** 题目标题 */
    private String title;

    /** 题目描述 MD 文件 ID */
    private Long contentFileId;

    /** 赛事类型：MCM_ICM / CUMCM */
    private String contestType;

    /** 难度：1=简单 2=中等 3=困难 */
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 3, message = "难度最大为 3")
    private Integer difficulty;

    /** 状态 */
    @Min(value = 0, message = "状态最小为 0")
    @Max(value = 3, message = "状态最大为 3")
    private Integer status;

    /** 标签 ID 列表（传 null 表示不修改，传空列表表示清空所有标签） */
    private List<Long> tagIds;

    /** 外部链接列表（传 null 表示不修改，传空列表表示清空所有链接） */
    private List<LinkItem> links;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkItem {
        private String title;
        private String url;
        private String description;
        private Integer sortOrder;
    }
}
