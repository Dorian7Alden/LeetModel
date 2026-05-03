package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Problem;
import com.senior.leetmodelbackend.pojo.vo.admin.ProblemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProblemMapper {

    List<ProblemVO> getProblemList();

    Problem getProblemById(Integer problemId);

    void insertProblem(Problem problem);

    void updateProblem(Problem problem);

    @Delete("delete from problem where problem_id = #{problemId}")
    void deleteProblem(Integer problemId);
}
