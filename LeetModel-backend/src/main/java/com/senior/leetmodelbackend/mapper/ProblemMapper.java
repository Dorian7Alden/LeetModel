package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.dto.problem.ProblemQueryDTO;
import com.senior.leetmodelbackend.pojo.entity.Problem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProblemMapper {

    List<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);

    @Select("SELECT * FROM problem WHERE id = #{id}")
    Problem selectById(Integer id);

    @Insert("INSERT INTO problem (title, content_url) VALUES (#{title}, #{contentUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertProblem(Problem problem);

}
