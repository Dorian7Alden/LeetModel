package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.entity.dto.ProblemQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Problem;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {
    @Autowired
    private ProblemService problemService;


    @GetMapping
    public Result<List<Problem>> getProblemList(@RequestParam ProblemQueryDTO problemQueryDTO) {

        List<Problem> allProblems = problemService.getProblemsByQueryDTO(problemQueryDTO);

        if (allProblems.isEmpty()) {
            return Result.error(404, "没有题目");
        }
        return Result.success(allProblems);
    }

}
