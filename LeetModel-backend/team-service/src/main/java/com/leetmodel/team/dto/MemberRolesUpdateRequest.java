package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新成员专业角色请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberRolesUpdateRequest {

    @NotNull(message = "建模手状态不能为空")
    private Boolean modeler;

    @NotNull(message = "编程手状态不能为空")
    private Boolean programmer;

    @NotNull(message = "论文手状态不能为空")
    private Boolean writer;
}
