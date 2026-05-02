package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.dto.RegisterDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import com.senior.leetmodelbackend.validator.auth.RegisterParamValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
public class Register extends AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final RegisterParamValidator registerParamValidator;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO request) {

        registerParamValidator.validate(request);

        String email = request.getEmail();
        String code = request.getCode();

        // 是否已经注册
        if (userService.getUserByEmail(email) != null) {
            return Result.error(ResponseCode.USER_ALREADY_EXISTS);
        }

        // 验证码是否正确
        if (!verificationCodeService.verifyCode(email, code)) {
            return Result.error(ResponseCode.VERIFICATION_CODE_INCORRECT);
        }

        // 完成注册
        try {
            userService.register(request);
            return Result.success("注册成功");
        } catch (Exception e) {
            log.error("注册失败", e);
            return Result.error(ResponseCode.SYSTEM_INTERNAL_ERROR, "注册失败");
        }
    }

}
