package com.senior.leetmodelbackend.service;

import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Problem;

public interface ProblemService {
    PageInfo<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);
}
