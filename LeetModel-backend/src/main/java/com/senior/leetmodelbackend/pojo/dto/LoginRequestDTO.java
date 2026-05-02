package com.senior.leetmodelbackend.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginRequestDTO {
    @Email(message = "邮箱格式错误")
    @NotNull(message = "邮箱不能为空")
    @NotBlank(message = "邮箱不能为空")
    String email;

    @NotNull(message = "密码不能为空")
    @NotBlank(message = "密码不能为空")
    String password;
}
