package com.senior.leetmodelbackend.controller.problem;

import com.senior.leetmodelbackend.pojo.dto.problem.ProblemUploadDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.ProblemService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
public class InsertProblem extends ProblemController {

    private final ProblemService problemService;


    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Result<String> uploadProblem(@ModelAttribute ProblemUploadDTO problemUploadDTO) {

        problemService.uploadProblem(problemUploadDTO);
        System.out.println(problemUploadDTO);

        return Result.success("正在开发中...");
    }

}
