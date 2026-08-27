package com.leetmodel.common.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 权限 VO（只读展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
