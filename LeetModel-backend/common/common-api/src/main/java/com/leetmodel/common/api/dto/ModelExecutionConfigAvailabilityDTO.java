package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 网关对模型执行配置引用的只读发布检查结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelExecutionConfigAvailabilityDTO {
    private String modelExecutionConfigVersion;
    private Boolean available;
    private String callType;
    private String reason;
}
