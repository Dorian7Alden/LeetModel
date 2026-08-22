package com.leetmodel.team.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新团队信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamUpdateRequest {

    @Size(min = 1, max = 64, message = "团队名称最多64位")
    private String name;

    @Size(max = 256, message = "团队描述最多256位")
    private String description;
}
