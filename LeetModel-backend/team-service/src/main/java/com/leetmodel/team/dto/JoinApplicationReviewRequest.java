package com.leetmodel.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核入队申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinApplicationReviewRequest {

    @NotBlank(message = "审核决定不能为空")
    private String decision;
}
