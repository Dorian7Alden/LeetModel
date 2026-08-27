package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 供练习链路使用的题目摘要。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPracticeDTO {
    private Long id;
    /** 题号：短顺序编号，面向用户展示。 */
    private Integer code;
    private String title;
    private Integer durationMinutes;
    private Integer status;
}
