package com.leetmodel.common.core.bean;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分页查询基类 —— 所有前台列表与管理端分页检索入参的统一父类。
 *
 * <p>各业务模块的分页查询参数对象（PageQuery）均继承此类，并在子类中扩展业务筛选条件。</p>
 *
 * <h3>核心设计思考与面试考点</h3>
 * <ul>
 *   <li><b>分页安全防御（防慢 SQL 与堆内存 OOM）：</b><br/>
 *       强制使用 {@code @Max(100)} 对单页拉取上限施加硬约束。若缺乏条数限制，恶意调用方或前端 Bug 传入
 *       {@code pageSize=1000000} 会触发全表扫库并加载海量数据到 JVM 堆中，极易引起频繁 Full GC 甚至内存溢出崩溃。</li>
 *   <li><b>1-based 索引规范与组件对齐：</b><br/>
 *       强制约束 {@code @Min(1)}，与国内主流前端 UI 库（Element Plus、Ant Design）及主流 ORM 分页插件保持 1-based 习惯统一。</li>
 * </ul>
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
