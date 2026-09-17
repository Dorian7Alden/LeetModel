package com.leetmodel.common.core.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页列表数据响应载体。
 *
 * <p>封装标准分页结构：total（总条数）、page（当前页）、size（每页大小）、rows（数据列表）。
 * 作为防腐层隔离持久层 IPage 对象，避免 ORM 内部属性外泄；当无记录时保证返回空集合而非 null。</p>
 *
 * @param <T> 列表项数据的实际类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 符合条件的总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private int page;

    /** 每页条数上限 */
    private int size;

    /** 当前页数据记录集，永不为 null */
    private List<T> rows;

    /**
     * 将 MyBatis-Plus 的 IPage 分页源对象转换为通用领域分页模型。
     *
     * @param page MyBatis-Plus 分页结果对象，不能为 null
     * @param <T>  数据实体或 DTO 类型
     * @return 转换后的 PageResult 实例；若原 records 为 null 则自动转换为空列表
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getRecords() != null
                        ? page.getRecords()
                        : Collections.emptyList()
        );
    }
}
