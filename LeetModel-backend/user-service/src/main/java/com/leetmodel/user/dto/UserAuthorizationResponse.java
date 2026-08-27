package com.leetmodel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前用户角色和权限响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthorizationResponse {

    private List<String> roles;
    private List<String> permissions;
}
