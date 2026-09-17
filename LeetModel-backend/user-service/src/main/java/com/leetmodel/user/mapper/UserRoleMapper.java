package com.leetmodel.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.mapper.model.RoleUserCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 按角色聚合仍存在的用户数。
     *
     * <p>历史数据可能保留已逻辑删除用户的中间表记录，管理端关系计数只统计当前有效用户。</p>
     *
     * @param roleIds 角色 ID 集合
     * @return 角色与当前有效用户数
     */
    @Select("""
            <script>
            SELECT ur.role_id AS role_id, COUNT(DISTINCT ur.user_id) AS user_count
            FROM user_role ur
            INNER JOIN user u ON u.id = ur.user_id AND u.deleted = 0
            WHERE ur.role_id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
            </foreach>
            GROUP BY ur.role_id
            </script>
            """)
    List<RoleUserCountRow> countActiveUsersByRoleIds(@Param("roleIds") List<Long> roleIds);
}
