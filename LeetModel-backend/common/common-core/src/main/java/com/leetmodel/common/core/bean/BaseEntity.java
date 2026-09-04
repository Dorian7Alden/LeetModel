package com.leetmodel.common.core.bean;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化实体基类。
 *
 * <p>统一封装主键 id（雪花算法自动生成）、创建时间 createTime、更新时间 updateTime
 * 与逻辑删除标记 deleted（0 正常，1 删除，配合 MyBatis-Plus @TableLogic）。</p>
 *
 * <p>注意：雪花算法生成的 64 位 Long 主键输出给前端时会丢失精度，须经 Jackson 全局转为 String。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，由雪花算法生成 */
    private Long id;

    /** 创建时间，插入时自动填充 */
    private LocalDateTime createTime;

    /** 最后更新时间，插入与更新时自动填充 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常，1 已删除 */
    @TableLogic
    private Integer deleted;
}
