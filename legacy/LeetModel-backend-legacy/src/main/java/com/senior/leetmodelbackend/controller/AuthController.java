package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.common.utils.JwtUtil;
import com.senior.leetmodelbackend.pojo.dto.LoginRequestDTO;
import com.senior.leetmodelbackend.pojo.dto.LogoutDTO;
import com.senior.leetmodelbackend.pojo.dto.RegisterDTO;
import com.senior.leetmodelbackend.pojo.dto.ResetPasswordDTO;
import com.senior.leetmodelbackend.pojo.dto.SendEmailCodeDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.vo.LoginVO;
import com.senior.leetmodelbackend.service.TokenService;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import com.senior.leetmodelbackend.validator.auth.LoginParamValidator;
import com.senior.leetmodelbackend.validator.auth.RegisterParamValidator;
import com.senior.leetmodelbackend.validator.auth.ResetPasswordParamValidator;
import com.senior.leetmodelbackend.validator.auth.SendEmailCodeParamValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final TokenService tokenService;

    private final LoginParamValidator loginParamValidator;
    private final RegisterParamValidator registerParamValidator;
    private final SendEmailCodeParamValidator sendEmailCodeParamValidator;
    private final ResetPasswordParamValidator resetPasswordParamValidator;

    /**
     * 邮箱 + 密码登录，返回 JWT token 与用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequestDTO request) {
        loginParamValidator.validate(request);

        String email = request.getEmail();
        User user = userService.authenticate(email, request.getPassword());
        String role = userService.determineRole(user.getUserId());

        log.info("用户 {} 登录成功", email);
        String token = JwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getUsername(), role);

        return Result.success("登录成功", LoginVO.createVO(user, role, token));
    }

    /**
     * 邮箱验证码注册，默认分配 MEMBER 角色
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterDTO request) {
        registerParamValidator.validate(request);

        if (!verificationCodeService.verifyCode(request.getEmail(), request.getCode())) {
            throw new BusinessException(ResponseCode.VERIFICATION_CODE_INCORRECT);
        }

        userService.register(request);
        return Result.success("注册成功");
    }

    /**
     * 退出登录，将 token 加入 Redis 黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody LogoutDTO request) {
        tokenService.blacklist(request.getToken());
        return Result.success("退出登录成功");
    }

    /**
     * 通过邮箱验证码重置密码，重置成功后自动登录
     */
    @PostMapping("/reset-password")
    public Result<LoginVO> resetPassword(@RequestBody ResetPasswordDTO request) {
        resetPasswordParamValidator.validate(request);

        if (!verificationCodeService.verifyCode(request.getEmail(), request.getCode())) {
            throw new BusinessException(ResponseCode.VERIFICATION_CODE_INCORRECT);
        }

        userService.resetPassword(request.getEmail(), request.getPassword());

        User user = userService.getUserByEmail(request.getEmail());
        String role = userService.determineRole(user.getUserId());
        String token = JwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getUsername(), role);

        return Result.success("重置密码成功", LoginVO.createVO(user, role, token));
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/verification-codes")
    public Result<Void> sendCode(@RequestBody SendEmailCodeDTO request) {
        sendEmailCodeParamValidator.validate(request);
        verificationCodeService.sendEmailCode(request.getEmail());
        return Result.success("验证码发送成功");
    }
}
