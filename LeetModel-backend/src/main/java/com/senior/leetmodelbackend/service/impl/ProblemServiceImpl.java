package com.senior.leetmodelbackend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.common.utils.OssUtils;
import com.senior.leetmodelbackend.pojo.dto.problem.ProblemQueryDTO;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.pojo.dto.problem.ProblemUploadDTO;
import com.senior.leetmodelbackend.pojo.dto.tag.CreateProblemTagDTO;
import com.senior.leetmodelbackend.pojo.entity.Problem;
import com.senior.leetmodelbackend.service.ProblemService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;
    private final OssUtils ossUtils;

    @Override
    public PageInfo<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO) {

        // 实现分页查询逻辑
        Integer pageNum = problemQueryDTO.getPageNum();
        Integer pageSize = problemQueryDTO.getPageSize();

        // 默认分页设置
        if (pageNum == null) pageNum = 1;
        if (pageSize == null) pageSize = 10;

        // 使用 PageHelper 进行分页查询
        PageHelper.startPage(pageNum, pageSize);
        List<Problem> problems = problemMapper.getProblemsByQueryDTO(problemQueryDTO);

        return new PageInfo<>(problems);
    }




    @Override
    public void uploadProblem(ProblemUploadDTO problemUploadDTO) {

        // 1. 上传文件
        String problemContentMdUrl = ossUtils.uploadFile(problemUploadDTO.getContentMarkdownFile());

        // 2. 上传题目
        Problem problem = new Problem();
        problem.setTitle(problemUploadDTO.getTitle());
        problem.setContentUrl(problemContentMdUrl);
        // TODO: creator_id
        insertProblem(problem);

        // 3. 上传没有的标签
        // TODO: 标签的id不是自增！得手动管理
        List<CreateProblemTagDTO> TagList = problemUploadDTO.getTagList();
        List<CreateProblemTagDTO> newTagList = TagList.stream()
                .filter(tag -> tag.getTagId() == null)
                .toList();
        // TODO: 还是同一个对象吗？

        // 4. 将标签与题目建立关联


        // 5. 关联链接

    }

    @Override
    public void insertProblem(Problem problem) {
        problemMapper.insertProblem(problem);
    }
}
