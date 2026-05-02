package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import org.springframework.stereotype.Component;

@Component
public class PermissionParamValidator implements ParamValidator<PermissionDTO> {

    @Override
    public void validate(PermissionDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request.getName(), "权限名称不能为空")
                .hasLength(request.getCode(), "权限编码不能为空")
                .validateAndThrow();
    }
}
