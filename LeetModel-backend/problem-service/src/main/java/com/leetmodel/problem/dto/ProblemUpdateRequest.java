package com.leetmodel.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "(?s).*\\S.*", message = "题目标题不能为空")
    @Size(max = 255, message = "题目标题不能超过 255 个字符")
    private String title;

    /** 题目描述 MD 文件 ID */
    @Positive(message = "题目描述文件 ID 必须为正数")
    private Long contentFileId;

    @Positive(message = "赛事 ID 必须为正数")
    private Long contestId;

    @Min(value = 2000, message = "题目年份不能早于 2000 年")
    @Max(value = 2100, message = "题目年份不能晚于 2100 年")
    private Integer year;

    @Pattern(regexp = "ZH|EN", message = "题面语言只支持 ZH 或 EN")
    private String statementLanguage;

    @Min(value = 1, message = "完成时长至少为 1 分钟")
    @Max(value = 10080, message = "完成时长不能超过 10080 分钟")
    private Integer durationMinutes;

    public void setContestType(String contestType) {
        this.contestId = "MCM_ICM".equals(contestType) ? 1L : 2L;
    }

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
    @Valid
    private List<LinkItem> links;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkItem {
        @NotBlank(message = "链接标题不能为空")
        @Size(max = 200, message = "链接标题不能超过 200 个字符")
        private String title;

        @NotBlank(message = "链接地址不能为空")
        @Size(max = 1024, message = "链接地址不能超过 1024 个字符")
        @Pattern(regexp = "https?://.+", message = "链接地址必须使用 HTTP 或 HTTPS")
        private String url;

        @Size(max = 255, message = "链接说明不能超过 255 个字符")
        private String description;

        @Min(value = 0, message = "链接排序权重不能小于 0")
        private Integer sortOrder;
    }
}
