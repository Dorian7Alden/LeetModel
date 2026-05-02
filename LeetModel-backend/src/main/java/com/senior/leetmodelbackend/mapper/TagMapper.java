package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Tag;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper {

    @Select("select * from tag")
    public List<Tag> getAllTags();

    @Select("select * from tag where category_id = #{categoryId};")
    public List<Tag> getTagsByCategoryId(Integer categoryId);

}
