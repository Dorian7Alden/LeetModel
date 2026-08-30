package com.leetmodel.admin.dto;

import com.leetmodel.common.api.dto.AssistantProductionChangeApplyDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理页面第二次确认请求，只能引用服务端冻结的变更标识。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAssistantProductionChangeApplyDTO {
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{32}")
    private String changeRequestId;

    /**
     * 注入服务端认证上下文中的管理员身份。
     * @param operatorId 当前登录管理员
     * @return 内部业务服务确认命令
     */
    public AssistantProductionChangeApplyDTO toInternal(Long operatorId) {
        return new AssistantProductionChangeApplyDTO(changeRequestId, operatorId);
    }
}
