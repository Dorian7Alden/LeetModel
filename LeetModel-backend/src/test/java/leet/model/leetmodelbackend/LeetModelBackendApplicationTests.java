package leet.model.leetmodelbackend;

import leet.model.leetmodelbackend.property.JwtProperties;
import leet.model.leetmodelbackend.property.QQMailProperties;
import leet.model.leetmodelbackend.property.RedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "QQ_MAIL_USER=test-user",
        "QQ_MAIL_AUTH_CODE=test-auth-code",
        "REDIS_HOST=127.0.0.1",
        "REDIS_PASSWORD=test-password"
})
class LeetModelBackendApplicationTests {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private QQMailProperties qqMailProperties;

    @Autowired
    private RedisProperties redisProperties;

    @Test
    void contextLoads() {
        assertNotNull(jwtProperties);
        assertEquals("dXNlcl9zZWNyZXRfa2V5X2Zvcl9qd3RfYXV0aF9kZW1vXzI1NmJpdF8xMjM=", jwtProperties.getSecret());
        assertEquals(86400000L, jwtProperties.getExpiration());

        assertNotNull(qqMailProperties);
        assertEquals("smtp.qq.com", qqMailProperties.getHost());
        assertEquals(465, qqMailProperties.getPort());
        assertEquals("test-user", qqMailProperties.getUsername());
        assertEquals("test-auth-code", qqMailProperties.getPassword());
        assertEquals("UTF-8", qqMailProperties.getDefaultEncoding());
        assertNotNull(qqMailProperties.getProperties());

        assertNotNull(redisProperties);
        assertEquals("127.0.0.1", redisProperties.getHost());
        assertEquals(6379, redisProperties.getPort());
        assertEquals("test-password", redisProperties.getPassword());
    }

}
