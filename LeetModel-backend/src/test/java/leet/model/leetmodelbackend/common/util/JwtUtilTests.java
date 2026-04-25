package leet.model.leetmodelbackend.common.util;

import leet.model.leetmodelbackend.property.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具类测试，覆盖签发、解析和基础校验逻辑。
 */
@SpringBootTest(properties = {
        "QQ_MAIL_USER=test-user@example.com",
        "QQ_MAIL_AUTH_CODE=test-auth-code",
        "REDIS_HOST=127.0.0.1",
        "REDIS_PASSWORD=test-password"
})
class JwtUtilTests {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 验证 token 创建后可以正常解析出 subject、claims 和有效性状态。
     */
    @Test
    void shouldCreateAndParseToken() {
        String token = jwtUtil.createToken("user-1", Map.of("role", "admin", "userId", 1001L));

        assertTrue(jwtUtil.isValid(token));
        assertEquals("user-1", jwtUtil.getSubject(token));
        assertEquals("admin", jwtUtil.getClaim(token, "role", String.class));
        assertEquals(1001L, jwtUtil.getClaim(token, "userId", Long.class));
        assertFalse(jwtUtil.isExpired(token));
        assertNotNull(jwtUtil.parseClaims(token));
        assertEquals("dXNlcl9zZWNyZXRfa2V5X2Zvcl9qd3RfYXV0aF9kZW1vXzI1NmJpdF8xMjM=", jwtProperties.getSecret());
    }
}