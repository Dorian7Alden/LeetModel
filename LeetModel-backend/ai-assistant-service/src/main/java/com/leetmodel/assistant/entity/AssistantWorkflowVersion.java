package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_workflow_version")
public class AssistantWorkflowVersion extends BaseEntity {
    private String workflowVersion;
    private String name;
    private String status;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String toolsetVersion;
    private String ragMode;
    private String inputSchema;
    private String outputSchema;
    private String compatibility;
    private String impactScope;
    private Boolean experimentCandidate;
}
