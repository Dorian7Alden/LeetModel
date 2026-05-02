package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.dto.PostQueryDTO;
import com.senior.leetmodelbackend.pojo.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    List<Post> selectPostList(@Param("query") PostQueryDTO query);

    Long selectPostCount(@Param("query") PostQueryDTO query);

}
