package com.leetmodel.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
