package com.leetmodel.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.user.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
