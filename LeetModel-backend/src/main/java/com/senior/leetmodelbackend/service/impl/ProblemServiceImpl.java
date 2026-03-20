package com.senior.leetmodelbackend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.entity.pojo.Problem;
import com.senior.leetmodelbackend.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemServiceImpl implements ProblemService {
    @Autowired
    private ProblemMapper problemMapper;

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
}
