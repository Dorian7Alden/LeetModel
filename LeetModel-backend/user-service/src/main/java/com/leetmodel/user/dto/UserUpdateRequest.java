package com.leetmodel.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户个人信息更新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 32, message = "昵称最多32位")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 64, message = "邮箱最多64位")
    private String email;
}
