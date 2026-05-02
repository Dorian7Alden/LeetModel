package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.dto.SendEmailCodeDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import com.senior.leetmodelbackend.validator.auth.SendEmailCodeParamValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VerificationCodes extends AuthController {

    private final SendEmailCodeParamValidator sendEmailCodeParamValidator;
    private final VerificationCodeService verificationCodeService;

    @PostMapping("/verification-codes")
    public Result<Void> sendCode(@RequestBody SendEmailCodeDTO request) {
        sendEmailCodeParamValidator.validate(request);
        verificationCodeService.sendEmailCode(request.getEmail());
        return Result.success("验证码发送成功");
    }
}
