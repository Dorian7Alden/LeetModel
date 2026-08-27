package com.leetmodel.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体。
 */
@Data
@TableName("role")
public class Role {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色编码：admin / vip / user */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
