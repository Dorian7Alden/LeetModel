package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建题目请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemCreateRequest {

    /** 题目标题 */
    @NotBlank(message = "题目标题不能为空")
    private String title;

    /** 题目描述 MD 文件 ID */
    private Long contentFileId;

    /** 赛事类型：MCM_ICM / CUMCM */
    @NotBlank(message = "赛事类型不能为空")
    private String contestType;

    /** 难度：1=简单 2=中等 3=困难 */
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 3, message = "难度最大为 3")
    private Integer difficulty;

    /** 状态：0=草稿 1=已发布，创建时默认为草稿 */
    private Integer status;

    /** 标签 ID 列表（可选） */
    private List<Long> tagIds;

    /** 外部链接列表（可选） */
    private List<LinkItem> links;

    /** 外部链接项 */
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
