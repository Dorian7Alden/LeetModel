package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 客服使用的已发布题目查询条件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProblemQueryDTO {

    @NotNull(message = "查询模式不能为空")
    private AssistantProblemQueryMode mode;

    @Min(value = 1001, message = "题号不能小于 1001")
    @Max(value = 10000, message = "题号不能大于 10000")
    private Integer code;

    @Size(max = 50, message = "关键词不能超过 50 个字符")
    private String keyword;

    private Boolean includeOverview;

    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "赛事编码格式不合法")
    private String contestCode;

    @Min(value = 2000, message = "题目年份不能早于 2000 年")
    @Max(value = 2100, message = "题目年份不能晚于 2100 年")
    private Integer year;

    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 3, message = "难度最大为 3")
    private Integer difficulty;

    @Pattern(regexp = "ZH|EN", message = "题面语言只支持 ZH 或 EN")
    private String statementLanguage;

    @Min(value = 30, message = "最大完成时长不能小于 30 分钟")
    @Max(value = 10080, message = "最大完成时长不能超过 10080 分钟")
    private Integer maxDurationMinutes;

    @Min(value = 1, message = "返回数量不能小于 1")
    @Max(value = 5, message = "返回数量不能超过 5")
    private Integer limit;

    /**
     * 校验 SEARCH 与 RECOMMEND 的字段边界。
     *
     * @return 模式字段是否匹配
     */
    @JsonIgnore
    @AssertTrue(message = "题目查询模式与条件不匹配")
    public boolean isModeFieldsValid() {
        if (mode == null) return true;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (keyword != null && !hasKeyword) return false;
        if (mode == AssistantProblemQueryMode.SEARCH) {
            boolean exactlyOneSearchKey = code != null ^ hasKeyword;
            boolean hasRecommendationFilter = contestCode != null || year != null
                    || difficulty != null || statementLanguage != null || maxDurationMinutes != null;
            return exactlyOneSearchKey && !hasRecommendationFilter;
        }
        return code == null && !Boolean.TRUE.equals(includeOverview);
    }
}
