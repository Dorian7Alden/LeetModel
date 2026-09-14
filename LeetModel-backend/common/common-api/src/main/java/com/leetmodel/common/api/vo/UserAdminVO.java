package com.leetmodel.common.api.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员视角用户详情 VO（含角色信息）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 用户拥有的角色列表 */
    private List<RoleSimpleVO> roles;

    /**
     * 角色简要信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleSimpleVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String code;
        private String name;
    }
}
