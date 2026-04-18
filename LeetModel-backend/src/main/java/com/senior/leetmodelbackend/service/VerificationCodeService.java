package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.enums.CaptchaGenType;
import com.senior.leetmodelbackend.pojo.enums.VerificationCodeType;
import com.senior.leetmodelbackend.common.exception.ErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.utils.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VerificationCodeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CODE_PREFIX = "LeetModel:"; // 缓存前缀
    private static final long DEFAULT_EXPIRATION_SECONDS = 5 * 60;

    private String generateRandomSixDigitCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    private Result<Void> doSendCode(VerificationCodeType type, String target, Long expirationSeconds,
            CaptchaGenType codeType) {
        if (type == VerificationCodeType.EMAIL) {
            return sendCodeToEmail(target, expirationSeconds, codeType);
        }
        // 这里如果有枚举兜底，可以不用异常，但为了安全
        return Result.error(ErrorCode.PARAM_VALIDATION_ERROR, "不支持的验证码类型");
    }

    /**
     * 发送验证码到邮箱核心实现
     */
    private Result<Void> sendCodeToEmail(String email, Long expirationSeconds, CaptchaGenType codeType) {
        String redisKey = CODE_PREFIX + email;

        // 1. 检查是否已经存在验证码
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            log.warn("邮箱 {} 已经存在验证码，请勿重复发送", email);
            return Result.error(ErrorCode.VERIFICATION_CODE_FREQUENT, "邮箱 " + email + " 已经存在验证码，请勿重复发送");
        }

        // 2. 生成验证码
        String code;
        if (codeType == CaptchaGenType.SIX_DIGIT) {
            code = generateRandomSixDigitCode();
        } else {
            return Result.error(ErrorCode.PARAM_VALIDATION_ERROR, "无效的验证码生成类型");
        }

        // 3. 缓存验证码
        redisTemplate.opsForValue().set(redisKey, code, expirationSeconds, TimeUnit.SECONDS);

        // 4. 发送邮件
        String emailContent = String.format(
                "LeetModel 网站提醒您，您当前正在通过邮箱注册账号，您的验证码是：%s\n%d分钟内有效。",
                code, expirationSeconds / 60);

        try {
            EmailUtils.sendEmail(email, "LeetModel 注册验证码", emailContent);
            log.info("验证码 {} 发送至邮箱: {}", code, email);
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            log.error("邮件发送失败: {}", email, e);
            // 发送失败，清理 Redis 缓存（回滚机制）
            redisTemplate.delete(redisKey);
            return Result.error(ErrorCode.EMAIL_SEND_FAILED, "邮件发送失败，请稍后重试");
        }
    }

    /**
     * 使用默认配置，当前仅支持发送邮箱验证码
     */
    public Result<Void> sendCode(String email) {
        return doSendCode(VerificationCodeType.EMAIL, email, DEFAULT_EXPIRATION_SECONDS, CaptchaGenType.SIX_DIGIT);
    }

    /**
     * 校验验证码
     */
    public Boolean verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + email);
        if (storedCode == null) {
            log.warn("邮箱 {} 的验证码已经过期，请重新发送验证码", email);
            return false;
        }
        if (storedCode.equals(code)) {
            log.info("邮箱 {} 验证码通过", email);
            redisTemplate.delete(CODE_PREFIX + email);
            return true;
        } else {
            log.warn("邮箱 {} 验证码比对失败", email);
            return false;
        }
    }
}