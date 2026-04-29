package leet.model.leetmodelbackend.validator.auth;

import leet.model.leetmodelbackend.common.validator.ParamValidator;
import leet.model.leetmodelbackend.common.validator.ParameterValidator;
import leet.model.leetmodelbackend.dto.auth.SendEmailCodeDTO;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 发送邮箱验证码接口的入参校验器。
 */
@Component
public class SendEmailCodeParamValidator implements ParamValidator<SendEmailCodeDTO> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public void validate(SendEmailCodeDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request.getEmail(), "邮箱不能为空")
                .isTrue(isEmailValid(request.getEmail()), "邮箱格式不正确")
                .validateAndThrow();
    }

    private boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
