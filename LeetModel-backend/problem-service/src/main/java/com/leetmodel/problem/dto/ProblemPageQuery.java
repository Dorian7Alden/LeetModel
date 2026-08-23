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

    /** 赛事类型筛选（可选） */
    @Pattern(regexp = "MCM_ICM|CUMCM", message = "赛事类型只支持 MCM_ICM 或 CUMCM")
    private String contestType;

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
