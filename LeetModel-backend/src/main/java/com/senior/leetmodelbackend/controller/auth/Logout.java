package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
public class Logout extends AuthController {

    private final RedisTemplate<String, String> redisTemplate;

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
                return Result.error(ErrorCode.UNAUTHORIZED_TOKEN_MISSING, "Token不能为空");
            }

            // 解析 token 获取过期时间
            Claims claims = com.senior.leetmodelbackend.utils.JwtUtil.parseToken(token);
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
            return Result.error(ErrorCode.UNAUTHORIZED_TOKEN_INVALID, "退出登录失败");
        }
    }

}
