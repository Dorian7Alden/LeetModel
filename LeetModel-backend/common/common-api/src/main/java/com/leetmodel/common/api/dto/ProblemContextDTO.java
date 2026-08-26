package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 业务使用的已发布题目上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemContextDTO {
    private Long id;
    private String title;
    private String contentMarkdown;
    private Integer durationMinutes;
    private Integer status;
}
