package com.leetmodel.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.result.Result;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关未登录响应构造工具。
 *
 * <p>Sa-Token 过滤器与黑名单过滤器共用同一份响应格式，避免同一类认证失败出现两种返回结构。</p>
 */
public final class UnauthorizedResponses {

    /** 未登录业务码，与 common-security 的认证异常处理器保持一致。 */
    public static final int UNAUTHORIZED_CODE = 40101;

    /** Token 已在黑名单中时的提示语。 */
    public static final String BLACKLISTED_MESSAGE = "登录已失效，请重新登录";

    /** 缺少或校验不通过时的提示语。 */
    public static final String UNAUTHENTICATED_MESSAGE = "未登录或 Token 已失效，请重新登录";

    private UnauthorizedResponses() {
        // 工具类禁止实例化
    }

    /**
     * 构造未登录响应体。
     *
     * @param objectMapper JSON 序列化器
     * @param message      提示语
     * @return JSON 字符串；序列化失败时退化为固定结构
     */
    public static String body(ObjectMapper objectMapper, String message) {
        try {
            return objectMapper.writeValueAsString(Result.fail(UNAUTHORIZED_CODE, message));
        } catch (JsonProcessingException exception) {
            return "{\"code\":" + UNAUTHORIZED_CODE + ",\"message\":\"" + message
                    + "\",\"data\":null,\"timestamp\":null}";
        }
    }

    /**
     * 直接写出 401 响应。
     *
     * @param exchange     当前请求上下文
     * @param objectMapper JSON 序列化器
     * @param message      提示语
     * @return 响应写入结果
     */
    public static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        DataBuffer buffer = response.bufferFactory()
                .wrap(body(objectMapper, message).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
