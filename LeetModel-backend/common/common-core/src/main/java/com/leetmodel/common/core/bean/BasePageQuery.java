package com.leetmodel.common.core.bean;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询入参基类。
 *
 * <p>各模块前台列表与管理端检索入参继承此类扩展。页码遵循 1-based 规范（从 1 开始）；
 * 单页条数默认 20 条，硬约束最大上限 100 条以防御慢 SQL 与 JVM 堆内存 OOM。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasePageQuery {

    /** 当前页码，从 1 开始 */
    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    /** 每页记录条数，默认 20 条，最大上限 100 条 */
    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private int pageSize = 20;
}
