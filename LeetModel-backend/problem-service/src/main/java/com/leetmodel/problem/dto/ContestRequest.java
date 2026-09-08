package com.leetmodel.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新赛事基础数据的管理请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContestRequest {

    @NotBlank(message = "赛事编码不能为空")
    @Size(max = 32, message = "赛事编码不能超过32个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "赛事编码只能包含字母、数字、下划线和短横线")
    private String code;

    @NotBlank(message = "赛事名称不能为空")
    @Size(max = 100, message = "赛事名称不能超过100个字符")
    private String name;

    @Size(max = 200, message = "英文名称不能超过200个字符")
    private String englishName;

    @Size(max = 100, message = "赛程周期描述不能超过100个字符")
    private String scheduleDesc;

    @Size(max = 100, message = "组队规程说明不能超过100个字符")
    private String teamRules;

    @Size(max = 150, message = "成果交付规范不能超过150个字符")
    private String submissionSpec;

    @Size(max = 150, message = "赛题命题范式不能超过150个字符")
    private String problemSpec;

    @Size(max = 500, message = "赛事简介不能超过500个字符")
    private String description;

    @Size(max = 255, message = "官方主页链接不能超过255个字符")
    private String officialUrl;
}
