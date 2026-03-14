package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select * from user where email = #{email}")
    public User getUserByEmail(String email);


}
