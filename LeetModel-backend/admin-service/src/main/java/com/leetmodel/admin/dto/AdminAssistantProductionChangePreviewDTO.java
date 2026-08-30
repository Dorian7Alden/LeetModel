package com.leetmodel.admin.dto;

import com.leetmodel.common.api.dto.AssistantProductionChangePreviewRequestDTO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理页面生产变更预览请求，不允许客户端声明操作者。 */
@Data
public class AdminAssistantProductionChangePreviewDTO {
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

    /**
     * 注入服务端认证上下文中的管理员身份。
     * @param operatorId 当前登录管理员
     * @return 内部业务服务命令
     */
    public AssistantProductionChangePreviewRequestDTO toInternal(Long operatorId) {
        return new AssistantProductionChangePreviewRequestDTO(action, expectedRevision,
                targetWorkflowVersion, targetProductionConfigVersion, ragIndexVersion,
                reason, operatorId);
    }
}
