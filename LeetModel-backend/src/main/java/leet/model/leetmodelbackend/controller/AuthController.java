package leet.model.leetmodelbackend.controller;

import leet.model.leetmodelbackend.common.Result;
import leet.model.leetmodelbackend.dto.auth.SendEmailCodeDTO;
import leet.model.leetmodelbackend.service.auth.AuthCodeService;
import leet.model.leetmodelbackend.validator.auth.SendEmailCodeParamValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权相关接口。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SendEmailCodeParamValidator sendEmailCodeParamValidator;
    private final AuthCodeService authCodeService;

    @PostMapping("/email/code")
    public Result<Void> sendEmailCode(@RequestBody SendEmailCodeDTO request) {
        sendEmailCodeParamValidator.validate(request);
        authCodeService.sendEmailCode(request.getEmail());
        return Result.success(null, "验证码发送成功");
    }
}