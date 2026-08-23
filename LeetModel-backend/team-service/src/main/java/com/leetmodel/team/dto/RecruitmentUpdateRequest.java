package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新队伍招募配置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentUpdateRequest {

    @NotNull(message = "招募状态不能为空")
    private Boolean recruiting;

    @NotNull(message = "建模手需求不能为空")
    private Boolean needModeler;

    @NotNull(message = "编程手需求不能为空")
    private Boolean needProgrammer;

    @NotNull(message = "论文手需求不能为空")
    private Boolean needWriter;
}
