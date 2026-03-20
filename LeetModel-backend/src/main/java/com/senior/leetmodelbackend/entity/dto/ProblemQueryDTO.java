package com.senior.leetmodelbackend.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 题目查询请求参数
 */
@Data
public class ProblemQueryDTO {

    @Min(value = 1, message = "页码最小为1")
    Integer pageNum;
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 20, message = "每页条数最大为20")
    Integer pageSize;

    String keyword;
    String difficulty;
    String language;
    // 默认排序方式
    String sortOrder = "asc";
    Integer minAveScore;
    Integer maxAveScore;

}


