package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_production_config")
public class AssistantProductionConfig extends BaseEntity {
    private String productionConfigVersion;
    private String workflowVersion;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String toolsetVersion;
    private String ragMode;
    private String ragIndexVersion;
    private String ragIndexKey;
    private Long createdBy;
    private String reason;
}
