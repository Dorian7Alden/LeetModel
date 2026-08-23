package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户公开摘要 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicSummaryDTO {

    private Long userId;
    private String nickname;
    private String avatarUrl;
}
