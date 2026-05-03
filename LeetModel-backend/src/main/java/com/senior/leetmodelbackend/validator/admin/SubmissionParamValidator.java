package com.senior.leetmodelbackend.validator.admin;

import com.senior.leetmodelbackend.common.validator.ParamValidator;
import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.pojo.dto.admin.SubmissionDTO;
import org.springframework.stereotype.Component;

@Component
public class SubmissionParamValidator implements ParamValidator<SubmissionDTO> {

    @Override
    public void validate(SubmissionDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .notNull(request.getProblemId(), "题目ID不能为空")
                .hasLength(request.getTitle(), "作品标题不能为空")
                .notNull(request.getContentFileId(), "作品文件不能为空")
                .validateAndThrow();
    }
}
