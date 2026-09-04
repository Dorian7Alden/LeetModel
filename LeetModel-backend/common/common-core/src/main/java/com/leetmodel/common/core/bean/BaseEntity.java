package com.leetmodel.common.core.bean;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化实体基类 —— 全平台所有数据库实体实体的统一抽象父类。
 *
 * <p>提供通用的基础运维与审计字段：
 * <ul>
 *   <li>{@code id}：主键标识，基于雪花算法分布式生成</li>
 *   <li>{@code createTime}：记录创建时间，插入时自动填充</li>
 *   <li>{@code updateTime}：记录最后修改时间，插入与更新时自动刷新</li>
 *   <li>{@code deleted}：逻辑删除状态，0 代表正常，1 代表已逻辑删除</li>
 * </ul>
 * </p>
 *
 * <h3>核心设计思考与面试考点</h3>
 * <ul>
 *   <li><b>为什么采用雪花算法（Snowflake ID）而非自增主键？</b><br/>
 *       1. 避免分库分表与多数据源聚合时产生主键冲突；<br/>
 *       2. 阻断外部通过自增连续数值推算平台业务单量与增长趋势；<br/>
 *       3. ID 具有时间单调递增性，对 B+Tree 索引页的分裂与写入性能友好。</li>
 *   <li><b>雪花 ID 与前端精度丢失问题：</b><br/>
 *       Java 的 {@code Long} 为 64 位整数（最大值 9223372036854775807），而 JavaScript
 *       中 Number 类型的最大安全整数为 2^53 - 1（9007199254740991）。若直接以数值输出，
 *       前端解析时末三位会产生精度失真。全平台必须通过 Jackson 全局配置将 Long 统一序列化为 String 输出。</li>
 *   <li><b>逻辑删除与唯一索引冲突：</b><br/>
 *       配合 MyBatis-Plus {@link TableLogic} 注解，物理 DELETE 操作会自动转为 UPDATE deleted = 1。
 *       在设计唯一键（如 uk_username）时需注意与 deleted 的组合约束，防止已删除记录阻碍同名数据再次注册。</li>
 *   <li><b>序列化支持：</b><br/>
 *       实现 {@link Serializable} 接口，配合业务 Redis 二级缓存与跨进程状态同步，防止深拷贝或反序列化失败。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（MyBatis-Plus 雪花算法自动生成） */
    private Long id;

    /** 创建时间（数据库自动填充） */
    private LocalDateTime createTime;

    /** 最后更新时间（数据库自动填充） */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     * 0 = 正常，1 = 已删除。
     * 配合 MyBatis-Plus @TableLogic，delete 操作自动转换为 update deleted=1。
     */
    @TableLogic
    private Integer deleted;
}
