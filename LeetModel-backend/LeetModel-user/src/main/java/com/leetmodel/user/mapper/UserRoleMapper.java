package com.leetmodel.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
