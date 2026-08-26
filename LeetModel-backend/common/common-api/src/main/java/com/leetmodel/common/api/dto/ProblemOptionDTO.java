package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内部调用方使用的已发布题目选项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemOptionDTO {
    private Long id;
    private String title;
    private Long contestId;
    private Integer year;
    private String statementLanguage;
    private Integer difficulty;
    private Integer durationMinutes;
}
