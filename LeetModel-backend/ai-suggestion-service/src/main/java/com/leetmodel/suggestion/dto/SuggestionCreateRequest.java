package com.leetmodel.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuggestionCreateRequest {
    @NotNull(message = "提交标识不能为空")
    @Positive(message = "提交标识必须为正整数")
    private Long submissionId;

    @NotNull(message = "评审任务标识不能为空")
    @Positive(message = "评审任务标识必须为正整数")
    private Long reviewTaskId;

    @NotBlank(message = "客户端请求标识不能为空")
    @Size(max = 64, message = "客户端请求标识不能超过64个字符")
    private String clientRequestId;

    @Pattern(regexp = "VECTOR_RAG_V1", message = "正式论文建议当前只允许 VECTOR_RAG_V1")
    private String retrievalWorkflowVersion;
}
