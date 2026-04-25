package leet.model.leetmodelbackend.controller;

import leet.model.leetmodelbackend.common.Result;
import leet.model.leetmodelbackend.common.validator.ParameterValidator;
import leet.model.leetmodelbackend.dto.auth.SendEmailCodeDTO;
import leet.model.leetmodelbackend.service.auth.AuthCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 鉴权相关接口。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final AuthCodeService authCodeService;

    /**
     * 发送邮箱验证码
     *
     * @param request 邮箱地址
     * @return 响应结果
     */
    @PostMapping("/email/code")
    public Result<Void> sendEmailCode(@RequestBody SendEmailCodeDTO request) {
        ParameterValidator.init()
                .notNull(request, "请求体不能为空")
                .hasLength(request == null ? null : request.getEmail(), "邮箱不能为空")
                .isTrue(request == null || isEmailValid(request.getEmail()), "邮箱格式不正确")
                .validateAndThrow();

        authCodeService.sendEmailCode(request.getEmail());
        return Result.success(null, "验证码发送成功");
    }

    private boolean isEmailValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}