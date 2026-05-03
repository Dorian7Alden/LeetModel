package com.senior.leetmodelbackend.validator.user;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import org.springframework.stereotype.Component;

@Component
public class UserIdParamValidator implements ParamValidator<Integer> {

    @Override
    public void validate(Integer userId) {
        ParameterValidator.init()
                .notNull(userId, "用户ID不能为空")
                .isTrue(userId > 0, "用户ID必须大于0")
                .validateAndThrow();
    }
}
