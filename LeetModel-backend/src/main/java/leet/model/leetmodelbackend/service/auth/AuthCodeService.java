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

    /** 验证码邮件模板 classpath 路径 */
    private static final String EMAIL_CODE_TEMPLATE_PATH = "templates/mail/email-code.html";

    /** 用于生成 6 位随机验证码的 SecureRandom 实例 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 邮件发送工具 */
    private final MailUtil mailUtil;

    /** Redis 字符串操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 验证码相关配置 */
    private final AuthCodeProperties authCodeProperties;

    /**
     * 发送邮箱验证码：限频检查、生成代码、渲染模板、发送邮件并缓存到 Redis。
     */
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

    /** 将验证码等变量替换到邮件模板中，生成最终 HTML 邮件内容 */
    private String renderEmailCodeMailContent(String code) {
        String template = loadEmailCodeTemplate();
        return template
                .replace("${code}", code)
                .replace("${validMinutes}", String.valueOf(authCodeProperties.getRedisCodeExpire().toMinutes()))
                .replace("${supportEmail}", "support@mathmodel.com");
    }

    /** 从 classpath 加载验证码邮件模板 */
    private String loadEmailCodeTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(EMAIL_CODE_TEMPLATE_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_SEND_FAILED);
        }
    }

    /** 尝试设置发送冷却锁，true 表示获取成功可以发送，false 表示仍在冷却期内 */
    private boolean acquireSendCooldown(String cooldownKey) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", authCodeProperties.getRedisSendCooldown());
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            throw new BusinessException(ResponseCode.AUTH_EMAIL_CODE_CACHE_FAILED);
        }
    }

    /** 邮件发送或缓存失败时释放冷却锁，允许用户立即重试 */
    private void releaseCooldown(String cooldownKey) {
        try {
            stringRedisTemplate.delete(cooldownKey);
        } catch (RuntimeException ignored) {
            // Ignore cooldown cleanup failures to preserve original business error.
        }
    }

    /** 拼接验证码 Redis key */
    private String buildCodeKey(String email) {
        return authCodeProperties.getRedisCodeKeyPrefix() + email;
    }

    /** 拼接冷却期 Redis key */
    private String buildCooldownKey(String email) {
        return authCodeProperties.getRedisCooldownKeyPrefix() + email;
    }

    /** 生成 6 位数字验证码 */
    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}