package com.leetmodel.problem.dto;

import com.leetmodel.common.core.dto.BasePageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 题目分页查询参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProblemPageQuery extends BasePageQuery {

    @Positive(message = "赛事 ID 必须为正数")
    private Long contestId;

    @Min(value = 2000, message = "题目年份不能早于 2000 年")
    @Max(value = 2100, message = "题目年份不能晚于 2100 年")
    private Integer year;

    @Pattern(regexp = "ZH|EN", message = "题面语言只支持 ZH 或 EN")
    private String statementLanguage;

    public void setContestType(String contestType) {
        this.contestId = "MCM_ICM".equals(contestType) ? 1L : 2L;
    }

    /** 难度筛选（可选） */
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 3, message = "难度最大为 3")
    private Integer difficulty;

    /** 状态筛选（可选，公开接口固定传 1） */
    @Min(value = 0, message = "状态最小为 0")
    @Max(value = 3, message = "状态最大为 3")
    private Integer status;

    /** 标签 ID 筛选（可选） */
    @Positive(message = "标签 ID 必须为正数")
    private Long tagId;

    /** 标题关键词搜索（可选） */
    @Size(max = 100, message = "关键词不能超过 100 个字符")
    private String keyword;
}
