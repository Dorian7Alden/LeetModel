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

    // 默认排序方式
    String sortOrder = "asc";

//    // 排序字段：full_score-分数 difficulty-难度 create_time-发布时间
//    private String sortField = "id";

//    // 题目类型筛选
//    private String problemType;
//
//    // 专题类型筛选
//    private String specialType;
//
//    // 标签ID列表（多标签筛选，交集匹配）
//    private List<Long> tagIdList;

}


