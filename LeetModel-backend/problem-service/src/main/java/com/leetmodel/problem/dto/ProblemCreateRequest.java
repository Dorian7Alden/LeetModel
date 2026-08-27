package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "题目标题不能超过 255 个字符")
    private String title;

    /** 可直接渲染的 Markdown 题面，可为空 */
    private String contentMarkdown;

    @NotNull(message = "赛事不能为空")
    @Positive(message = "赛事 ID 必须为正数")
    private Long contestId;

    @NotNull(message = "题目年份不能为空")
    @Min(value = 2000, message = "题目年份不能早于 2000 年")
    @Max(value = 2100, message = "题目年份不能晚于 2100 年")
    private Integer year;

    @NotBlank(message = "题面语言不能为空")
    @Pattern(regexp = "ZH|EN", message = "题面语言只支持 ZH 或 EN")
    private String statementLanguage;

    @NotNull(message = "完成时长不能为空")
    @Min(value = 1, message = "完成时长至少为 1 分钟")
    @Max(value = 10080, message = "完成时长不能超过 10080 分钟")
    private Integer durationMinutes;

    /** 难度：1=简单 2=中等 3=困难 */
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 3, message = "难度最大为 3")
    private Integer difficulty;

    /** 状态：0=草稿 1=已发布，创建时默认为草稿 */
    @Min(value = 0, message = "状态最小为 0")
    @Max(value = 3, message = "状态最大为 3")
    private Integer status;

    /** 标签 ID 列表（可选） */
    private List<Long> tagIds;

}
