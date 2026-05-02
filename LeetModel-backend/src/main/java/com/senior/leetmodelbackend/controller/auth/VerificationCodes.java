package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AllArgsConstructor
public class VerificationCodes extends AuthController {

    private final VerificationCodeService verificationCodeService;

    /**
     * 发送验证码
     */
    @PostMapping("/verification-codes")
    public Result<Void> sendCode(@RequestBody Map<String, String> params) {
        // TODO: 发送验证码之前进行人机验证，完成验证后，携带验证的令牌
        // TODO: 适配多种验证码发送方式：邮箱、手机等类型。目前只支持邮箱验证码
        // 由于返回信息在 service 层进行了处理，这里直接返回结果
        String email = params.get("target");
        return verificationCodeService.sendCode(email);
    }


}
