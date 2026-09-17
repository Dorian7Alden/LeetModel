package com.leetmodel.common.core.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Actuator 管理端点的最小访问边界。
 */
public final class ManagementAccessPolicy {

    /** 管理端点根路径。 */
    public static final String ACTUATOR_PATH = "/actuator";
    /** Prometheus 与运维工具使用的受信 Header。 */
    public static final String TOKEN_HEADER = "X-LeetModel-Management-Token";

    private final byte[] trustedToken;

    /**
     * 创建管理端点访问策略。
     *
     * @param token 运行环境提供的可选管理 Token
     */
    public ManagementAccessPolicy(String token) {
        this.trustedToken = token == null || token.isBlank()
                ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 判断当前请求是否可以访问管理端点。
     *
     * <p>无详情的健康探针可由编排系统直接访问；其他端点只接受本机
     * 或匹配运行环境 Token 的请求。不信任 X-Forwarded-For。</p>
     *
     * @param requestPath 请求路径
     * @param remoteAddress 容器观测到的直连地址
     * @param presentedToken 请求中的管理 Token
     * @return 是否允许
     */
    public boolean isAllowed(String requestPath, String remoteAddress, String presentedToken) {
        if (isHealthPath(requestPath)) return true;
        if (trustedToken != null) {
            return presentedToken != null && MessageDigest.isEqual(
                    trustedToken,
                    presentedToken.getBytes(StandardCharsets.UTF_8)
            );
        }
        return isLoopback(remoteAddress);
    }

    /**
     * 判定指定请求路径是否属于 Actuator 管理端点路径。
     *
     * @param requestPath HTTP 请求路径字符串
     * @return true 表示属于 /actuator 及其子路径，false 表示普通业务路径
     */
    public static boolean isManagementPath(String requestPath) {
        return requestPath != null && (requestPath.equals(ACTUATOR_PATH)
                || requestPath.startsWith(ACTUATOR_PATH + "/"));
    }

    /**
     * 判定指定请求路径是否为健康检查探针路径。
     *
     * @param requestPath HTTP 请求路径字符串
     * @return true 表示属于 /actuator/health 探针路径，允许无 Token 直接访问
     */
    private static boolean isHealthPath(String requestPath) {
        String healthPath = ACTUATOR_PATH + "/health";
        return requestPath != null && (requestPath.equals(healthPath)
                || requestPath.startsWith(healthPath + "/"));
    }

    /**
     * 判定来源 IP 地址是否为本机环回地址。
     *
     * @param remoteAddress 容器接收到的直连来源 IP
     * @return true 表示为本机 127.0.0.1 或 IPv6 环回地址，允许免 Token 访问指标
     */
    private static boolean isLoopback(String remoteAddress) {
        return remoteAddress != null && (remoteAddress.startsWith("127.")
                || "::1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress));
    }
}
