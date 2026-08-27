package com.leetmodel.common.core.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应体 —— 配合 MyBatis-Plus 分页插件使用。
 *
 * <p>典型用法：将 IPage 转成 PageResult，再包一层 Result.ok()：</p>
 * <pre>{@code
 * IPage<User> page = userService.page(query);
 * return Result.ok(PageResult.from(page));
 * }</pre>
 *
 * <p>页码采用 1-based（首页 = 1），与前端常用分页组件保持一致。</p>
 *
 * @param <T> 列表元素的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 当前页码（1-based） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 当前页数据 */
    private List<T> rows;

    /**
     * 从 MyBatis-Plus 分页结果转换。
     *
     * @param page MyBatis-Plus {@link IPage} 对象
     * @param <T>  数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getRecords() != null ? page.getRecords() : Collections.emptyList()
        );
    }
}
