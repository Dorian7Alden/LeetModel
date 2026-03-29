package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.pojo.dto.LoginRequestDTO;
import com.senior.leetmodelbackend.pojo.dto.ResetPasswordDTO;
import com.senior.leetmodelbackend.pojo.enums.error.GlobalErrorCode;
import com.senior.leetmodelbackend.pojo.enums.error.UserErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.vo.LoginVO;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import com.senior.leetmodelbackend.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    @Autowired
    private VerificationCodeService verificationCodeService;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {

        String emailLogin = loginRequestDTO.getEmail();
        String passwordLogin = loginRequestDTO.getPassword(); // TODO: 前端密码是否已经加密？

        // 校验参数完整性
        if (emailLogin.isEmpty() || emailLogin.isBlank()) {
            log.error("邮箱登录失败 {} -----> 邮箱不能为空", emailLogin);
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "邮箱不能为空");
        }
        if (passwordLogin.isEmpty() || passwordLogin.isBlank()) {
            log.error("密码登录失败 {} -----> 密码不能为空", passwordLogin);
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "密码不能为空");
        }

        // TODO: 格式校验，不管
        // TODO: 适配多种登录方式？目前之后邮箱具有唯一性，所以只能支持使用邮箱登录
        // TODO: 使用 Spring Security 实现登录功能
        // TODO: 前端密码是否已经加密？
        // TODO: token 缓存机制，避免用户重复登录，保证每个用户仅唯一 token

        User userQuery = userService.getUserByEmail(emailLogin);

        // 业务异常
        if (userQuery == null) {
            log.error("邮箱登录失败 {} -----> 用户不存在", emailLogin);
            return Result.error(UserErrorCode.USER_NOT_FOUND);
        }
        if (!userQuery.getPassword().equals(passwordLogin)) {
            log.error("用户 {} 登录失败 -----> 密码错误", emailLogin);
            return Result.error(UserErrorCode.PASSWORD_INCORRECT);
        }

        // 登录成功
        log.info("用户 {} 登录成功", emailLogin);
        LoginVO loginVO = new LoginVO();

        // 生成并刷新 token
        String token = JwtUtil.generateToken(1000 * 3600 * 24 * 3); // 24 * 3 小时的登录令牌
        loginVO.setToken(token); // 设置 token 到 loginVO
        log.info("生成用户 {} 的登录 token: {}", emailLogin, token);
        // 封装查询到的用户信息
        System.out.println("=====================================================");
        System.out.println(userQuery);
        BeanUtils.copyProperties(userQuery, loginVO);
        System.out.println(loginVO);
        System.out.println("=====================================================");

        return Result.success("登录成功", loginVO);
    }

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

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, Object> params) {

        String email = (String) params.get("email");
        String code = (String) params.get("code");

        // 是否已经注册
        if (userService.getUserByEmail(email) != null) {
            return Result.error(UserErrorCode.USER_ALREADY_EXISTS);
        }

        // 验证码是否正确
        if (!verificationCodeService.verifyCode(email, code)) {
            return Result.error(UserErrorCode.VERIFICATION_CODE_INCORRECT);
        }

        // 完成注册
        try {
            userService.register(params);
            return Result.success("注册成功");
            // TODO: 完成验证码之后，验证码失效处理！！
        } catch (Exception e) {
            return Result.error(GlobalErrorCode.SYSTEM_INTERNAL_ERROR, "注册失败");
        }
    }

    /***
     * 找回密码
     */
    @PostMapping("/reset-password")
    public Result<LoginVO> resetPassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        if (resetPasswordDTO.getEmail() == null || resetPasswordDTO.getEmail().isBlank()) {
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "邮箱不能为空");
        } else if (resetPasswordDTO.getCode() == null || resetPasswordDTO.getCode().isBlank()) {
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "验证码不能为空");
        } else if (resetPasswordDTO.getPassword() == null || resetPasswordDTO.getPassword().isBlank()) {
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "密码不能为空");
        }
        // 验证码校验
        if (!verificationCodeService.verifyCode(resetPasswordDTO.getEmail(), resetPasswordDTO.getCode())) {
            return Result.error(UserErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        // 更新密码
        userService.resetPassword(resetPasswordDTO.getEmail(), resetPasswordDTO.getPassword());
        // 完成登录
        LoginVO loginVO = new LoginVO();
        User user = userService.getUserByEmail(resetPasswordDTO.getEmail());
        BeanUtils.copyProperties(user, loginVO);
        loginVO.setToken(JwtUtil.generateToken(1000 * 3600 * 24 * 3));

        return Result.success("重置密码成功", loginVO);
    }

    /**
     * 退出登录
     * 防止 token 被滥用，将 token 加入 redis 黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody Map<String, String> request) {
        try {
            // 从请求体中获取token
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                // TODO: 校验退出登录请求体中的 token 与当前用户是否匹配
                return Result.error(UserErrorCode.UNAUTHORIZED_TOKEN_MISSING, "Token不能为空");
            }

            // 解析 token 获取过期时间
            Claims claims = JwtUtil.parseToken(token);
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();

            // 计算 token 剩余有效期
            long remainingTime = expirationTime - currentTime;

            if (remainingTime > 0) {
                // 将 token 加入 Redis 黑名单，设置过期时间为剩余有效期
                redisTemplate.opsForValue().set("token:blacklist:" + token, "1", remainingTime);
                log.info("Token 已加入黑名单: {}", token);
            }

            return Result.success("退出登录成功");
        } catch (Exception e) {
            log.error("退出登录失败: {}", e.getMessage());
            return Result.error(UserErrorCode.UNAUTHORIZED_TOKEN_INVALID, "退出登录失败");
        }
    }
}
