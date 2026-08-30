package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 客服可用于实验或生产激活的不可变工作流发布项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionWorkflowDTO {
    private String workflowVersion;
    private String name;
    private String status;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String ragMode;
    private String inputSchema;
    private String outputSchema;
    private String compatibility;
    private String impactScope;
    private Boolean experimentCandidate;
}
