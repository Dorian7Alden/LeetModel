package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AllArgsConstructor
public class Register extends AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, Object> params) {

        String email = (String) params.get("email");
        String code = (String) params.get("code");

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
            userService.register(params);
            return Result.success("注册成功");
            // TODO: 完成验证码之后，验证码失效处理！！
        } catch (Exception e) {
            return Result.error(ResponseCode.SYSTEM_INTERNAL_ERROR, "注册失败");
        }
    }


}
