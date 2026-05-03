package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.UserBatchUpdateDTO;
import org.springframework.stereotype.Component;

@Component
public class UserBatchUpdateValidator implements ParamValidator<UserBatchUpdateDTO> {

    @Override
    public void validate(UserBatchUpdateDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .notNull(request.getUserIds(), "用户ID列表不能为空")
                .isTrue(request.getUserIds() != null && !request.getUserIds().isEmpty(), "用户ID列表不能为空")
                .isTrue(hasUpdateField(request), "至少需要提供一个更新字段")
                .validateAndThrow();
    }

    private boolean hasUpdateField(UserBatchUpdateDTO dto) {
        return dto.getSchool() != null || dto.getPhone() != null || dto.getStatus() != null;
    }
}
