package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交入队申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinApplicationCreateRequest {

    @NotNull(message = "招募位置不能为空")
    private Long recruitmentId;

    @Size(max = 256, message = "申请说明最多256位")
    private String message;
}
