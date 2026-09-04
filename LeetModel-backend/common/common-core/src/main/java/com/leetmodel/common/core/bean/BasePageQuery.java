package com.leetmodel.common.core.bean;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分页查询基类 —— 所有分页查询 DTO 的父类。
 *
 * <p>用法：各模块的 PageQuery DTO 继承此类，添加自己的查询条件即可。</p>
 *
 * <pre>{@code
 * public class ProblemPageQuery extends BasePageQuery {
 *     private String keyword;
 *     private Integer status;
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasePageQuery {

    /** 当前页码（1-based） */
    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private int pageSize = 20;
}
