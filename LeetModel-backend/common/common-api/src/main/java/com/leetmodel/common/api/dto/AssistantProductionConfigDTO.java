package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 一份不可变生产配置；当前项额外携带指针 revision 和观察期。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionConfigDTO {
    private String productionConfigVersion;
    private String workflowVersion;
    private String workflowName;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String ragMode;
    private String ragIndexVersion;
    private String impactScope;
    private Long revision;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private LocalDateTime observationUntil;
}
