package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内部生产变更预览请求；operatorId 只能由 admin 登录上下文注入。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionChangePreviewRequestDTO {
    @NotBlank
    @Pattern(regexp = "ACTIVATE|ROLLBACK")
    private String action;
    @NotNull
    @Min(1)
    private Long expectedRevision;
    @Size(max = 64)
    @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
    private String targetWorkflowVersion;
    @Size(max = 64)
    @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
    private String targetProductionConfigVersion;
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,127}")
    private String ragIndexVersion;
    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;
    @NotNull
    private Long operatorId;
}
