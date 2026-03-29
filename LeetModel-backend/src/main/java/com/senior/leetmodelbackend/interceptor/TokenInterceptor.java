package com.senior.leetmodelbackend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.leetmodelbackend.pojo.enums.error.UserErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌拦截器
 */
@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 解决返回数据乱码问题，统一设置为 UTF-8 编码
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        // 获取当前的请求 uri
        String requestURI = request.getRequestURI();
        log.info("请求通过拦截器: {}", requestURI);

        String token = request.getHeader("token");
        ObjectMapper mapper = new ObjectMapper();

        // token 为空
        if (token == null || token.isEmpty()) {
            log.info("令牌为空，请求头中缺少token");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(UserErrorCode.UNAUTHORIZED_TOKEN_MISSING)));
            return false;
        }

        // 检查 token 是否在黑名单中
        if (redisTemplate.hasKey("token:blacklist:" + token)) {
            log.info("令牌已被加入黑名单: {}", token);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(UserErrorCode.UNAUTHORIZED_TOKEN_INVALID)));
            return false;
        }

        // 校验 token
        try {
            JwtUtil.parseToken(token); // TODO: 这里应该是校验令牌，而不是解析令牌
        } catch (Exception e) {
            log.info("令牌解析失败");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(UserErrorCode.UNAUTHORIZED_TOKEN_INVALID)));
            return false;
        }

        log.info("token 校验通过，放行");
        return true;
    }
}
