package com.leetmodel.problem.dto;

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

    /** 可直接渲染的 Markdown 题面，空字符串表示清空 */
    private String contentMarkdown;

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

}
