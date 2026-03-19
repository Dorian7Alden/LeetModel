package com.senior.leetmodelbackend.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.entity.pojo.Problem;
import com.senior.leetmodelbackend.service.ProblemService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemServiceImpl implements ProblemService {
    @Autowired
    private ProblemMapper problemMapper;

    @Override
    public List<Problem> getProblemsByQueryDTO(ProblemQueryDTO problemQueryDTO) {

//        // 实现分页查询逻辑
//        Integer pageNum = problemQueryDTO.getPageNum();
//        Integer pageSize = problemQueryDTO.getPageSize();
//
//        // TODO: 分页查询
//        Page<Object> page = PageHelper.startPage(pageNum, pageSize);
//        List<Problem> problems = problemMapper.getProblemsByQueryDTO(problemQueryDTO);
//


        return List.of();
    }
}
