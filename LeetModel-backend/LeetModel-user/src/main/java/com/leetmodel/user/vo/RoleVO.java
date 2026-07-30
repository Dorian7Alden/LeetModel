package com.leetmodel.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色 VO。
 *
 * @author LeetModel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
