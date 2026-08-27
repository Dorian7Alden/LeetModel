package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建团队请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamCreateRequest {

    @NotNull(message = "题目不能为空")
    @Positive(message = "题目 ID 必须为正数")
    private Long problemId;

    @NotBlank(message = "团队名称不能为空")
    @Size(max = 64, message = "团队名称最多64位")
    private String name;

    @Size(max = 256, message = "团队描述最多256位")
    private String description;

}
