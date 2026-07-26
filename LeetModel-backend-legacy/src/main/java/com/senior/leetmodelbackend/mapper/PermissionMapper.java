package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Permission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper {

    @Select("select * from permission order by permission_id")
    List<Permission> getAllPermissions();

    @Select("select * from permission where permission_id = #{permissionId}")
    Permission getPermissionById(Integer permissionId);

    @Select("select * from permission where code = #{code}")
    Permission getPermissionByCode(String code);

    void insertPermission(Permission permission);

    void updatePermission(Permission permission);

    @Delete("delete from permission where permission_id = #{permissionId}")
    void deletePermission(Integer permissionId);
}
