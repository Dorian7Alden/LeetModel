package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 跨业务的版本化知识检索请求，不允许携带论文全文或绝对路径。 */
@Data
public class KnowledgeRetrievalRequestDTO {
    @NotBlank
    @Size(max = 40)
    private String workflowVersion;

    @NotBlank
    @Size(max = 4000)
    private String query;

    @Size(max = 40)
    private String scene;

    @Size(max = 100)
    private String requiredIndexVersion;

    @Min(1)
    @Max(20)
    private Integer topK;

    @Min(128)
    @Max(12000)
    private Integer tokenBudget;
}
