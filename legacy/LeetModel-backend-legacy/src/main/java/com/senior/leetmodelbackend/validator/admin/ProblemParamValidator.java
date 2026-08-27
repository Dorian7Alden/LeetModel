package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.ProblemDTO;
import org.springframework.stereotype.Component;

@Component
public class ProblemParamValidator implements ParamValidator<ProblemDTO> {

    @Override
    public void validate(ProblemDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request.getProblemTitle(), "题目标题不能为空")
                .notNull(request.getContentFileId(), "题目内容文件不能为空")
                .isTrue(request.getProblemStatus() == null
                        || (request.getProblemStatus() >= 0 && request.getProblemStatus() <= 3),
                        "题目状态必须在 0-3 之间")
                .validateAndThrow();
    }
}
