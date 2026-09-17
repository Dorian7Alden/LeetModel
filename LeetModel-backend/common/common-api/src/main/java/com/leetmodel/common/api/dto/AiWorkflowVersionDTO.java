package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 业务能力所有者发布的不可变工作流版本说明。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowVersionDTO {
    private String workflowVersion;
    private String name;
    private String status;
    private String inputSchema;
    private String outputSchema;
    private String compatibility;
}
