package com.senior.leetmodelbackend.service;

import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.pojo.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.pojo.entity.Problem;

public interface ProblemService {
    PageInfo<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);
}
