package com.leetmodel.common.core.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果载体 —— 专用于封装分页列表数据的泛型传输容器。
 *
 * <p>通常配合统一响应体使用：{@code Result.ok(PageResult.from(page))}</p>
 *
 * <h3>核心设计思考与架构约束</h3>
 * <ul>
 *   <li><b>防腐层设计（Anticorruption Layer）：为什么不直接返回 MyBatis-Plus 的 IPage 对象？</b><br/>
 *       1. 解耦持久层技术选型：{@code IPage} 带有 MyBatis-Plus 框架特有的内部实现属性（如 countId、optimizeCountSql、
 *          searchCount、orders 等），直接暴露会导致 ORM 框架细节向接口调用方渗透；<br/>
 *       2. 契约纯粹性：对外接口仅需暴露当前页码（page）、单页大小（size）、总记录数（total）与数据记录（rows），
 *          使用 {@code PageResult} 能够保证网络层契约与底层数据持久化技术彻底解耦。</li>
 *   <li><b>防御性编程（空安全防崩溃）：</b><br/>
 *       在 {@link #from(IPage)} 转换时，若持久层返回的记录集为 null，会自动转换为 {@link Collections#emptyList()}，
 *       杜绝返回 null 导致前端调用 {@code data.rows.map()} 时触发 TypeError 崩溃。</li>
 * </ul>
 *
 * @param <T> 列表元素的实际类型
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
                page.getRecords() != null
                        ? page.getRecords()
                        : Collections.emptyList()
        );
    }
}
