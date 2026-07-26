package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@AllArgsConstructor
public class TokenService {

    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token) {
        Claims claims;
        try {
            claims = JwtUtil.parseToken(token);
        } catch (Exception e) {
            log.debug("Token 解析失败，跳过黑名单: {}", e.getMessage());
            return;
        }

        long remainingTime = claims.getExpiration().getTime() - System.currentTimeMillis();

        if (remainingTime > 0) {
            redisTemplate.opsForValue()
                    .set(BLACKLIST_KEY_PREFIX + token, "1", Duration.ofMillis(remainingTime));
            log.info("Token 已加入黑名单: {}...", token.substring(0, Math.min(20, token.length())));
        }
    }

    public boolean isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
    }
}
