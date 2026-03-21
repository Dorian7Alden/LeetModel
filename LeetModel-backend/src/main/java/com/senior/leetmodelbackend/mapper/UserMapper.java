package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.entity.pojo.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("select * from user where email = #{email}")
    User getUserByEmail(String email);

    @Insert("insert into user (user.id, username, email, password, create_time, update_time) values (#{id}, #{username}, #{email}, #{password}, NOW(), NOW())")
    void insertUser(User user);

    @Select("select * from user where id = #{userId}")
    User getUserById(Integer userId);

    @Delete("delete from user where id = #{userId}")
    void deleteUserById(Integer userId);

    @Select("select MAX(id) from user;")
    Integer getMaxUserId();

    void updateUserById(User user);

    @Update("update user set password = #{password}, update_time = NOW() where email = #{email}")
    void updateUserPassword(String email, String password);
}
