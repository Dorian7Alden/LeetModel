package com.leetmodel.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联实体。
 */
@Data
@TableName("role_permission")
public class RolePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long roleId;
    private Long permissionId;
}
