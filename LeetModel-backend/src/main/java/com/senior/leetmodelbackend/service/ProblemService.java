package com.senior.leetmodelbackend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.OssFileMapper;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.ProblemDTO;
import com.senior.leetmodelbackend.pojo.entity.OssFile;
import com.senior.leetmodelbackend.pojo.entity.PageResult;
import com.senior.leetmodelbackend.pojo.entity.Problem;
import com.senior.leetmodelbackend.pojo.vo.admin.ProblemVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@AllArgsConstructor
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final OssFileMapper ossFileMapper;

    public PageResult<ProblemVO> getProblemList(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        var list = problemMapper.getProblemList();
        PageInfo<ProblemVO> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal());
    }

    public Problem getProblemById(Integer problemId) {
        Problem problem = problemMapper.getProblemById(problemId);
        if (problem == null) {
            throw new BusinessException(ResponseCode.PROBLEM_NOT_FOUND);
        }
        return problem;
    }

    public void createProblem(ProblemDTO dto, Integer creatorId) {
        OssFile ossFile = ossFileMapper.getOssFileById(dto.getContentFileId());
        if (ossFile == null) {
            throw new BusinessException(ResponseCode.FILE_NOT_FOUND);
        }
        Problem problem = new Problem();
        problem.setProblemTitle(dto.getProblemTitle());
        problem.setContentFileId(dto.getContentFileId());
        problem.setAveScore(BigDecimal.ZERO);
        problem.setProblemStatus(dto.getProblemStatus() != null ? dto.getProblemStatus() : 0);
        problem.setCreatorId(creatorId);
        problemMapper.insertProblem(problem);
        log.info("创建题目: {} [ID: {}]", problem.getProblemTitle(), problem.getProblemId());
    }

    public void updateProblem(Integer problemId, ProblemDTO dto) {
        getProblemById(problemId);
        if (dto.getContentFileId() != null) {
            OssFile ossFile = ossFileMapper.getOssFileById(dto.getContentFileId());
            if (ossFile == null) {
                throw new BusinessException(ResponseCode.FILE_NOT_FOUND);
            }
        }
        Problem problem = new Problem();
        problem.setProblemId(problemId);
        problem.setProblemTitle(dto.getProblemTitle());
        problem.setContentFileId(dto.getContentFileId());
        problem.setProblemStatus(dto.getProblemStatus());
        problemMapper.updateProblem(problem);
        log.info("更新题目: {}", problemId);
    }

    public void deleteProblem(Integer problemId) {
        getProblemById(problemId);
        problemMapper.deleteProblem(problemId);
        log.info("删除题目: {}", problemId);
    }
}
