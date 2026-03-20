package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProblemMapper {

    List<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);
}
