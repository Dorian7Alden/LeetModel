package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布一个招募位置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentUpdateRequest {

    @NotNull(message = "建模手需求不能为空")
    private Boolean needModeler;

    @NotNull(message = "编程手需求不能为空")
    private Boolean needProgrammer;

    @NotNull(message = "论文手需求不能为空")
    private Boolean needWriter;
}
