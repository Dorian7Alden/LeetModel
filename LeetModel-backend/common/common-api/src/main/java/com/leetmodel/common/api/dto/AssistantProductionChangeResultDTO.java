package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 生产变更确认结果；只有 APPLIED 表示指针发生变化。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionChangeResultDTO {
    private String changeRequestId;
    private String status;
    private String message;
    private AssistantProductionConfigDTO current;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditId;
}
