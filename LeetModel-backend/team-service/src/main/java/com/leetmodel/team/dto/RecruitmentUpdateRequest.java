package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 512, message = "招募说明不能超过512个字符")
    private String description;

    public RecruitmentUpdateRequest(Boolean needModeler, Boolean needProgrammer, Boolean needWriter) {
        this.needModeler = needModeler;
        this.needProgrammer = needProgrammer;
        this.needWriter = needWriter;
    }
}
