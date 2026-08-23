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

    @NotNull(message = "建模手意愿不能为空")
    private Boolean desiredModeler;

    @NotNull(message = "编程手意愿不能为空")
    private Boolean desiredProgrammer;

    @NotNull(message = "论文手意愿不能为空")
    private Boolean desiredWriter;

    @Size(max = 256, message = "申请说明最多256位")
    private String message;
}
