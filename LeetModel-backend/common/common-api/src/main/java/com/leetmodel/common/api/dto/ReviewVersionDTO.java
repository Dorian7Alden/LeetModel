package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可供管理端和质量评价选择的评审版本定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVersionDTO {
    private Long versionId;
    private String versionCode;
    private String name;
    private String description;
    private String processSummary;
    private String status;
}
