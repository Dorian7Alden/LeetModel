package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.AssignIdsDTO;
import org.springframework.stereotype.Component;

@Component
public class AssignIdsParamValidator implements ParamValidator<AssignIdsDTO> {

    @Override
    public void validate(AssignIdsDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .notNull(request.getIds(), "ID列表不能为空")
                .isTrue(request.getIds() != null && !request.getIds().isEmpty(), "ID列表不能为空")
                .validateAndThrow();
    }
}
