package com.senior.leetmodelbackend.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.common.utils.JwtUtil;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌拦截器
 */
@Slf4j
@Component
@AllArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String requestURI = request.getRequestURI();
        log.info("请求通过拦截器: {}", requestURI);

        String token = request.getHeader("token");
        ObjectMapper mapper = new ObjectMapper();

        if (token == null || token.isEmpty()) {
            log.info("令牌为空，请求头中缺少token");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(ResponseCode.UNAUTHORIZED_TOKEN_MISSING)));
            return false;
        }

        if (tokenService.isBlacklisted(token)) {
            log.info("令牌已被加入黑名单: {}", token);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(ResponseCode.UNAUTHORIZED_TOKEN_INVALID)));
            return false;
        }

        try {
            Claims claims = JwtUtil.parseToken(token);
            request.setAttribute("userId", claims.get("userId"));
            request.setAttribute("email", claims.get("email"));
            request.setAttribute("username", claims.get("username"));
            request.setAttribute("role", claims.get("role"));
        } catch (Exception e) {
            log.info("令牌解析失败");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter()
                    .write(mapper.writeValueAsString(Result.error(ResponseCode.UNAUTHORIZED_TOKEN_INVALID)));
            return false;
        }

        log.info("token 校验通过，放行");
        return true;
    }
}
