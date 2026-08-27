package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import org.springframework.stereotype.Component;

@Component
public class RoleParamValidator implements ParamValidator<RoleDTO> {

    @Override
    public void validate(RoleDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request.getName(), "角色名称不能为空")
                .hasLength(request.getCode(), "角色编码不能为空")
                .validateAndThrow();
    }
}
