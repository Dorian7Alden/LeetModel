package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Problem;

import java.util.List;

public interface ProblemService {
    List<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);
}
