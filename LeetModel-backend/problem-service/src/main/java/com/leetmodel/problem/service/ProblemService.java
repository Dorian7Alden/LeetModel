package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.vo.ProblemVO;

/**
 * 题目服务接口。
 */
public interface ProblemService extends IService<Problem> {

    /**
     * 分页查询题目（含标签名称）。
     */
    IPage<ProblemVO> pageProblems(ProblemPageQuery query);

    /**
     * 查询题目详情（含标签和链接）。
     */
    ProblemVO getProblemDetail(Long id);

    /**
     * 创建题目（含标签和链接）。
     */
    ProblemVO createProblem(ProblemCreateRequest request, Long creatorId);

    /**
     * 更新题目（含标签和链接替换）。
     */
    ProblemVO updateProblem(Long id, ProblemUpdateRequest request);

    /**
     * 获取标签名称列表。
     */
    java.util.List<String> getTagNames(Long problemId);
}
