package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 一次已经完整生效的生产配置变更审计。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionAuditDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditId;
    private String changeRequestId;
    private String action;
    private String fromProductionConfigVersion;
    private String toProductionConfigVersion;
    private Long fromRevision;
    private Long toRevision;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
    private String reason;
    private LocalDateTime changedAt;
}
