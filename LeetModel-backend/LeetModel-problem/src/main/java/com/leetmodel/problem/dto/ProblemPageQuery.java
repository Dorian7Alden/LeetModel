package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目分页查询参数。
 *
 * @author LeetModel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPageQuery {

    /** 页码（从 1 开始） */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page;

    /** 每页条数 */
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer pageSize;

    /** 赛事类型筛选（可选） */
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
    private Long tagId;

    /** 标题关键词搜索（可选） */
    private String keyword;
}
