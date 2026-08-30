package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 第二次确认只引用服务端冻结的变更请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionChangeApplyDTO {
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{32}")
    private String changeRequestId;
    @NotNull
    private Long operatorId;
}
