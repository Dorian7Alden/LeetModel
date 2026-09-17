package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_production_change_request")
public class AssistantProductionChangeRequest extends BaseEntity {
    private String changeRequestId;
    private String action;
    private Long expectedRevision;
    private Long sourceConfigId;
    private Long targetConfigId;
    private Long operatorId;
    private String reason;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime appliedAt;
    private String resultMessage;
}
