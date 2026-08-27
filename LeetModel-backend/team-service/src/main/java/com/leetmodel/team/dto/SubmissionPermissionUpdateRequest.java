package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新成员作品提交权限请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionPermissionUpdateRequest {

    @NotNull(message = "提交权限不能为空")
    private Boolean canSubmit;
}
