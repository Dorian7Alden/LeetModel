package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("select * from user where email = #{email}")
    User getUserByEmail(String email);

    @Insert("insert into user (email, password) values (#{email}, #{password})")
    void insertUser(User user);

}
