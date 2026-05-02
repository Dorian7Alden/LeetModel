package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.common.property.AuthCodeProperties;
import com.senior.leetmodelbackend.common.utils.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final String EMAIL_CODE_TEMPLATE_PATH = "templates/mail/email-code.html";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailUtils emailUtils;
    private final StringRedisTemplate redisTemplate;
    private final AuthCodeProperties authCodeProperties;

    public void sendEmailCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String cooldownKey = buildCooldownKey(normalizedEmail);

        if (!acquireSendCooldown(cooldownKey)) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_TOO_FREQUENT);
        }

        String code = generateCode();

        try {
            String mailContent = renderEmailCodeMailContent(code);
            emailUtils.sendHtmlMail(normalizedEmail, "LeetModel 邮箱验证码", mailContent);
        } catch (RuntimeException exception) {
            releaseCooldown(cooldownKey);
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_FAILED);
        }

        try {
            redisTemplate.opsForValue()
                    .set(buildCodeKey(normalizedEmail), code, authCodeProperties.getRedisCodeExpire());
        } catch (RuntimeException exception) {
            releaseCooldown(cooldownKey);
            throw new BusinessException(ResponseCode.AUTH_EMAIL_CODE_CACHE_FAILED);
        }

        log.info("验证码已发送至邮箱: {}", normalizedEmail);
    }

    public Boolean verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(buildCodeKey(email));
        if (storedCode == null) {
            log.warn("邮箱 {} 的验证码已过期", email);
            return false;
        }
        if (storedCode.equals(code)) {
            log.info("邮箱 {} 验证码校验通过", email);
            redisTemplate.delete(buildCodeKey(email));
            return true;
        }
        log.warn("邮箱 {} 验证码比对失败", email);
        return false;
    }

    private String renderEmailCodeMailContent(String code) {
        String template = loadEmailCodeTemplate();
        return template
                .replace("${code}", code)
                .replace("${validMinutes}", String.valueOf(authCodeProperties.getRedisCodeExpire().toMinutes()))
                .replace("${supportEmail}", "support@mathmodel.com");
    }

    private String loadEmailCodeTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(EMAIL_CODE_TEMPLATE_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_FAILED);
        }
    }

    private boolean acquireSendCooldown(String cooldownKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", authCodeProperties.getRedisSendCooldown());
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseCooldown(String cooldownKey) {
        try {
            redisTemplate.delete(cooldownKey);
        } catch (RuntimeException ignored) {
        }
    }

    private String buildCodeKey(String email) {
        return authCodeProperties.getRedisCodeKeyPrefix() + email;
    }

    private String buildCooldownKey(String email) {
        return authCodeProperties.getRedisCooldownKeyPrefix() + email;
    }

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
