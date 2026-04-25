package leet.model.leetmodelbackend.service.auth;

import leet.model.leetmodelbackend.common.error.BusinessException;
import leet.model.leetmodelbackend.common.error.ResponseCode;
import leet.model.leetmodelbackend.common.util.MailUtil;
import leet.model.leetmodelbackend.property.AuthCodeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * 鉴权验证码服务，负责生成验证码、发送邮件并缓存到 Redis。
 */
@Service
@RequiredArgsConstructor
public class AuthCodeService {

    private static final String EMAIL_CODE_TEMPLATE_PATH = "templates/mail/email-code.html";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MailUtil mailUtil;

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthCodeProperties authCodeProperties;

    public void sendEmailCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String cooldownKey = buildCooldownKey(normalizedEmail);

        if (!acquireSendCooldown(cooldownKey)) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_TOO_FREQUENT);
        }

        String code = generateCode();
        String mailContent;

        try {
            mailContent = renderEmailCodeMailContent(code);
            mailUtil.sendHtmlMail(
                    normalizedEmail,
                    "LeetModel 邮箱验证码",
                    mailContent
            );
        } catch (RuntimeException exception) {
            releaseCooldown(cooldownKey);
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_FAILED);
        }

        try {
            stringRedisTemplate.opsForValue()
                    .set(buildCodeKey(normalizedEmail), code, authCodeProperties.getRedisCodeExpire());
        } catch (RuntimeException exception) {
            releaseCooldown(cooldownKey);
            throw new BusinessException(ResponseCode.AUTH_EMAIL_CODE_CACHE_FAILED);
        }
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
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", authCodeProperties.getRedisSendCooldown());
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_CODE_CACHE_FAILED);
        }
    }

    private void releaseCooldown(String cooldownKey) {
        try {
            stringRedisTemplate.delete(cooldownKey);
        } catch (RuntimeException ignored) {
            // Ignore cooldown cleanup failures to preserve original business error.
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