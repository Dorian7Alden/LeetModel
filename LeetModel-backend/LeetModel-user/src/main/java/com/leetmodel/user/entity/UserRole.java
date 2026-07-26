package com.leetmodel.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体。
 *
 * @author LeetModel
 */
@Data
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long roleId;
}
