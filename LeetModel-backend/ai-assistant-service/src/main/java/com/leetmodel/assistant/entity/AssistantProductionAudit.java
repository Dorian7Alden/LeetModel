package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_production_audit")
public class AssistantProductionAudit extends BaseEntity {
    private String changeRequestId;
    private String action;
    private Long fromConfigId;
    private Long toConfigId;
    private Long fromRevision;
    private Long toRevision;
    private Long operatorId;
    private String reason;
    private LocalDateTime changedAt;
}
