//package com.senior.leetmodelbackend.controller.problem;
//
//import com.github.pagehelper.PageInfo;
//import com.senior.leetmodelbackend.pojo.dto.problem.ProblemQueryDTO;
//import com.senior.leetmodelbackend.pojo.entity.Problem;
//import com.senior.leetmodelbackend.pojo.entity.Result;
//import com.senior.leetmodelbackend.service.ProblemService;
//import lombok.AllArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.ModelAttribute;
//import org.springframework.web.bind.annotation.RestController;
//
//@AllArgsConstructor
//@RestController
//public class GetProblemList extends ProblemController {
//
//    private ProblemService problemService;
//
//    /**
//     * 获取题目列表，支持按关键字、难度、语言、分值范围及标签进行过滤
//     */
//    @GetMapping
//    public Result<PageInfo<Problem>> getProblemList(@ModelAttribute ProblemQueryDTO problemQueryDTO) {
//
//        return Result.success(problemService.getProblemsByQueryDTO(problemQueryDTO));
//    }
//}
