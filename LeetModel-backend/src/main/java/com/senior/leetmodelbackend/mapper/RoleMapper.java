package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("select * from role order by role_id")
    List<Role> getAllRoles();

    @Select("select * from role where role_id = #{roleId}")
    Role getRoleById(Long roleId);

    @Select("select * from role where code = #{code}")
    Role getRoleByCode(String code);

    void insertRole(Role role);

    void updateRole(Role role);

    @Select("delete from role where role_id = #{roleId}")
    void deleteRole(Long roleId);

    List<Permission> getPermissionsByRoleId(Long roleId);

    @Select("insert into role_permission (role_id, permission_id, create_time, update_time) values (#{roleId}, #{permissionId}, now(), now())")
    void insertRolePermission(Long roleId, Long permissionId);

    @Select("delete from role_permission where role_id = #{roleId}")
    void deleteRolePermissionsByRoleId(Long roleId);
}
