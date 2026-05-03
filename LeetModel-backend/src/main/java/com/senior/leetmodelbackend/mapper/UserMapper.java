package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.pojo.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from user where email = #{email}")
    User getUserByEmail(String email);

    @Insert("insert into user (username, email, password, create_time, update_time) values (#{username}, #{email}, #{password}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    void insertUser(User user);

    @Select("select * from user where user_id = #{userId}")
    User getUserById(Long userId);

    @Delete("delete from user where user_id = #{userId}")
    void deleteUserById(Long userId);

    void updateUserById(User user);

    @Update("update user set password = #{password}, update_time = NOW() where email = #{email}")
    void updateUserPassword(String email, String password);

    @Select("select user_id, username, email, school, avatar_file_id, status, create_time, update_time from user order by user_id")
    List<User> getAllUsers();

    List<Role> getRolesByUserId(Long userId);

    @Insert("insert into user_role (user_id, role_id, create_time, update_time) values (#{userId}, #{roleId}, now(), now())")
    void insertUserRole(Long userId, Long roleId);

    @Delete("delete from user_role where user_id = #{userId}")
    void deleteUserRolesByUserId(Long userId);
}
