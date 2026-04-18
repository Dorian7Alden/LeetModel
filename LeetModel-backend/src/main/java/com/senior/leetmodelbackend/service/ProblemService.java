package com.senior.leetmodelbackend.service;

import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.pojo.dto.problem.ProblemQueryDTO;
import com.senior.leetmodelbackend.pojo.dto.problem.ProblemUploadDTO;
import com.senior.leetmodelbackend.pojo.entity.Problem;

public interface ProblemService {
    PageInfo<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO);

    void uploadProblem(ProblemUploadDTO problemUploadDTO);

    void insertProblem(Problem problem);

}
