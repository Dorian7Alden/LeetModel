package com.senior.leetmodelbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Problem;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {
    @Autowired
    private ProblemService problemService;


    /**
     * 获取题目列表，支持按关键字、难度、语言、分值范围及标签进行过滤
     */
    @GetMapping
    public Result<PageInfo<Problem>> getProblemList(@ModelAttribute ProblemQueryDTO problemQueryDTO) {

        return Result.success(problemService.getProblemsByQueryDTO(problemQueryDTO));
    }

    


}
