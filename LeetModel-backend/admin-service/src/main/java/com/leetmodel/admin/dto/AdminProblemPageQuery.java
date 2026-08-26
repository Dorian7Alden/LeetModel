package com.leetmodel.admin.dto;

import com.leetmodel.common.core.dto.BasePageQuery;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/** 管理端题目筛选契约，与 problem-service 当前分页参数对齐。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminProblemPageQuery extends BasePageQuery {
    @Positive private Long contestId;
    @Min(2000) @Max(2100) private Integer year;
    @Pattern(regexp = "ZH|EN") private String statementLanguage;
    @Min(1) @Max(3) private Integer difficulty;
    @Min(0) @Max(3) private Integer status;
    @Size(max = 10) private List<@Positive Long> tagIds;
    @DecimalMin("0.00") @DecimalMax("100.00") private BigDecimal minAverageScore;
    @DecimalMin("0.00") @DecimalMax("100.00") private BigDecimal maxAverageScore;
    @Size(max = 100) private String keyword;
    @Pattern(regexp = "year|difficulty|averageScore") private String sortBy;
    @Pattern(regexp = "asc|desc") private String sortOrder;
}
