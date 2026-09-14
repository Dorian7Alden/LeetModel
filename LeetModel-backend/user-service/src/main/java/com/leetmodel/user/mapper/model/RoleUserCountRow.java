package com.leetmodel.user.mapper.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色有效用户数查询结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleUserCountRow {

    /** 角色 ID */
    private Long roleId;

    /** 当前有效用户数 */
    private Long userCount;
}
