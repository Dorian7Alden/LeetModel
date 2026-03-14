package com.senior.leetmodelbackend.interceptor;

import com.senior.leetmodelbackend.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 令牌拦截器
 */
@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        // 解决返回数据乱码问题，统一设置为 UTF-8 编码
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        // 获取当前的请求 uri
        String requestURI = request.getRequestURI();
        log.info("请求通过拦截器: {}", requestURI);

        String token = request.getHeader("token");

        // token 为空
        if (token == null || token.isEmpty()) {
            log.info("令牌为空，请求头中缺少token");
            //response.getWriter().write("令牌为空");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 返回 401 状态码，前端自己控制页面跳转
            return false;
        }

        // 校验 token
        try {
            JwtUtil.parseToken(token);
        } catch (Exception e) {
            log.info("令牌解析失败");
            //response.getWriter().write("令牌解析失败");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 返回 401 状态码，前端自己控制页面跳转
            return false;
        }

        log.info("token 校验通过，放行");
        return true;
    }
}
