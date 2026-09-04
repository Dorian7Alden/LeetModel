package com.leetmodel.common.core.bean;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 —— 所有数据库实体类的公共父类。
 *
 * <p>提供通用字段：
 * <ul>
 *   <li>{@code id} — 主键，由 MyBatis-Plus 雪花算法自动生成</li>
 *   <li>{@code createTime} — 创建时间，数据库自动填充</li>
 *   <li>{@code updateTime} — 更新时间，数据库自动填充</li>
 *   <li>{@code deleted} — 逻辑删除标记（0=正常，1=已删除），配合 @TableLogic 使用</li>
 * </ul>
 * </p>
 *
 * <p>实现 {@link Serializable} 以支持分布式缓存场景下的对象序列化。</p>
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
