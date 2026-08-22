package com.leetmodel.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限实体。
 *
 * @author LeetModel
 */
@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 权限编码：user:read, submission:create */
    private String code;

    /** 权限名称 */
    private String name;

    /** 权限描述 */
    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
