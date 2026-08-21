package com.senior.leetmodelbackend.validator.user;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.UserUpdateDTO;
import org.springframework.stereotype.Component;

@Component
public class UserUpdateDTOValidator implements ParamValidator<UserUpdateDTO> {

    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    @Override
    public void validate(UserUpdateDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .isTrue(hasUpdateField(request), "至少需要提供一个更新字段（username、school、phone、avatarFileId）")
                .isTrue(request.getUsername() == null || request.getUsername().length() <= 50, "用户名长度不能超过50")
                .isTrue(request.getSchool() == null || request.getSchool().length() <= 100, "学校名称长度不能超过100")
                .isTrue(request.getPhone() == null || request.getPhone().matches(PHONE_REGEX), "手机号格式不正确")
                .validateAndThrow();
    }

    private boolean hasUpdateField(UserUpdateDTO dto) {
        return dto.getUsername() != null
                || dto.getSchool() != null
                || dto.getPhone() != null
                || dto.getAvatarFileId() != null;
    }
}
