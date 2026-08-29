package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建前预估的一项候选工作流和执行配置。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCandidateDTO {
    @NotBlank(message = "工作流版本不能为空")
    @Size(max = 40, message = "工作流版本不能超过40个字符")
    private String workflowVersion;

    @NotBlank(message = "模型执行配置版本不能为空")
    @Size(max = 64, message = "模型执行配置版本不能超过64个字符")
    private String modelExecutionConfigVersion;

    @Size(max = 100, message = "RAG索引版本不能超过100个字符")
    private String ragIndexVersion;
}
