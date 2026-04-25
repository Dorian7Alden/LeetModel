package leet.model.leetmodelbackend.service.auth;

import leet.model.leetmodelbackend.common.error.BusinessException;
import leet.model.leetmodelbackend.common.error.ResponseCode;
import leet.model.leetmodelbackend.common.util.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "QQ_MAIL_USER=test-user@example.com",
        "QQ_MAIL_AUTH_CODE=test-auth-code",
        "REDIS_HOST=127.0.0.1",
        "REDIS_PASSWORD=test-password"
})
class AuthCodeServiceTests {

    @Autowired
    private AuthCodeService authCodeService;

    @Autowired
    private CapturingMailUtil capturingMailUtil;

    @Autowired
    private InMemoryStringRedisTemplate inMemoryStringRedisTemplate;

    @BeforeEach
    void setUp() {
        capturingMailUtil.clear();
        inMemoryStringRedisTemplate.clear();
    }

    @Test
    void shouldSendCodeAndCacheIntoRedisForFiveMinutes() {
        authCodeService.sendEmailCode("user@example.com");

        assertEquals("user@example.com", capturingMailUtil.lastTo);
        assertEquals("LeetModel 邮箱验证码", capturingMailUtil.lastSubject);
        assertNotNull(capturingMailUtil.lastContent);
        assertTrue(capturingMailUtil.lastContent.contains("<html"));
        assertTrue(capturingMailUtil.lastContent.contains("验证码"));
        assertTrue(capturingMailUtil.lastContent.contains("5 分钟"));

        String redisKey = "auth:email:code:user@example.com";
        String cachedCode = inMemoryStringRedisTemplate.values.get(redisKey);
        assertNotNull(cachedCode);
        assertEquals(6, cachedCode.length());
        assertTrue(cachedCode.chars().allMatch(Character::isDigit));
        assertEquals(Duration.ofMinutes(5), inMemoryStringRedisTemplate.expirations.get(redisKey));
    }

    @Test
    void shouldThrowBusinessExceptionWhenMailSendFailed() {
        capturingMailUtil.shouldThrow = true;

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> authCodeService.sendEmailCode("user@example.com")
        );

        assertEquals(ResponseCode.AUTH_EMAIL_SEND_FAILED, exception.getResponseCode());

        capturingMailUtil.shouldThrow = false;
        authCodeService.sendEmailCode("user@example.com");
        assertEquals(1, capturingMailUtil.sendCount);
    }

    @Test
    void shouldRejectDuplicateSendWithinCooldownWindow() {
        authCodeService.sendEmailCode("user@example.com");
        String redisKey = "auth:email:code:user@example.com";
        String firstCode = inMemoryStringRedisTemplate.values.get(redisKey);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> authCodeService.sendEmailCode("user@example.com")
        );

        assertEquals(ResponseCode.AUTH_EMAIL_SEND_TOO_FREQUENT, exception.getResponseCode());
        assertEquals(1, capturingMailUtil.sendCount);
        assertEquals(firstCode, inMemoryStringRedisTemplate.values.get(redisKey));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        CapturingMailUtil capturingMailUtil() {
            return new CapturingMailUtil();
        }

        @Bean
        @Primary
        InMemoryStringRedisTemplate inMemoryStringRedisTemplate() {
            return new InMemoryStringRedisTemplate();
        }
    }

    static class CapturingMailUtil extends MailUtil {

        private String lastTo;

        private String lastSubject;

        private String lastContent;

        private boolean shouldThrow;

        private int sendCount;

        CapturingMailUtil() {
            super(null, null);
        }

        @Override
        public void sendHtmlMail(String to, String subject, String content) {
            capture(to, subject, content);
        }

        @Override
        public void sendTextMail(String to, String subject, String content) {
            capture(to, subject, content);
        }

        private void capture(String to, String subject, String content) {
            if (shouldThrow) {
                throw new IllegalStateException("mail send failed");
            }
            this.sendCount++;
            this.lastTo = to;
            this.lastSubject = subject;
            this.lastContent = content;
        }

        void clear() {
            this.lastTo = null;
            this.lastSubject = null;
            this.lastContent = null;
            this.shouldThrow = false;
            this.sendCount = 0;
        }
    }

    static class InMemoryStringRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new HashMap<>();

        private final Map<String, Duration> expirations = new HashMap<>();

        private final ValueOperations<String, String> operations = new ValueOperations<>() {
            @Override
            public void set(String key, String value) {
                values.put(key, value);
            }

            @Override
            public void set(String key, String value, Duration timeout) {
                values.put(key, value);
                expirations.put(key, timeout);
            }

            @Override
            public String get(Object key) {
                return values.get(key);
            }

            @Override
            public Boolean setIfAbsent(String key, String value) {
                if (values.containsKey(key)) {
                    return false;
                }
                values.put(key, value);
                expirations.remove(key);
                return true;
            }

            @Override
            public Boolean setIfAbsent(String key, String value, Duration timeout) {
                if (values.containsKey(key)) {
                    return false;
                }
                values.put(key, value);
                expirations.put(key, timeout);
                return true;
            }

            @Override
            public Boolean setIfPresent(String key, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Boolean setIfPresent(String key, String value, Duration timeout) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void multiSet(Map<? extends String, ? extends String> map) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Boolean multiSetIfAbsent(Map<? extends String, ? extends String> map) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long increment(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long increment(String key, long delta) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Double increment(String key, double delta) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Integer append(String key, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String get(String key, long start, long end) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(String key, String value, long offset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long size(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Boolean setBit(String key, long offset, boolean value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Boolean getBit(String key, long offset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<String> multiGet(java.util.Collection<String> keys) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long delete(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long delete(java.util.Collection<String> keys) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long getAndDelete(String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAndSet(String key, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAndExpire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAndExpire(String key, Duration timeout) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAndPersist(String key) {
                throw new UnsupportedOperationException();
            }
        };

        @Override
        public ValueOperations<String, String> opsForValue() {
            return operations;
        }

        @Override
        public Boolean delete(String key) {
            boolean existed = values.containsKey(key) || expirations.containsKey(key);
            values.remove(key);
            expirations.remove(key);
            return existed;
        }

        void clear() {
            values.clear();
            expirations.clear();
        }
    }
}