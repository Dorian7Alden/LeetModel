package com.senior.leetmodelbackend.validator.user;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class UserEmailParamValidator implements ParamValidator<String> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public void validate(String email) {
        ParameterValidator.init()
                .hasLength(email, "查询的邮箱不能为空")
                .isTrue(isEmailValid(email), "邮箱格式不正确")
                .validateAndThrow();
    }

    private boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
