package com.senior.leetmodelbackend.validator.auth;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.LoginRequestDTO;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class LoginParamValidator implements ParamValidator<LoginRequestDTO> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public void validate(LoginRequestDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request.getEmail(), "邮箱不能为空")
                .isTrue(isEmailValid(request.getEmail()), "邮箱格式不正确")
                .hasLength(request.getPassword(), "密码不能为空")
                .validateAndThrow();
    }

    private boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
