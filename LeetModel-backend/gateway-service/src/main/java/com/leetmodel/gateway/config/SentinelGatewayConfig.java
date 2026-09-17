package com.leetmodel.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.leetmodel.gateway.handler.SentinelBlockRequestHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关流控与限流回调装配配置。
 *
 * <p>注册全局统一限流响应处理器，并基于路由 ID 初始化平台边缘防刷规则。</p>
 */
@Configuration
public class SentinelGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelGatewayConfig.class);

    private final SentinelBlockRequestHandler blockRequestHandler;

    public SentinelGatewayConfig(SentinelBlockRequestHandler blockRequestHandler) {
        this.blockRequestHandler = blockRequestHandler;
    }

    @PostConstruct
    public void init() {
        initBlockHandler();
        initDefaultFlowRules();
    }

    /**
     * 注册网关统一限流响应处理器。
     */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
        log.info("Sentinel GatewayCallbackManager block handler registered successfully");
    }

    /**
     * 初始化网关路由级默认流量整形规则。
     */
    public static void initDefaultFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 针对公开题库端点（高频访问）防爬虫过载保护
        GatewayFlowRule problemPublicRule = new GatewayFlowRule("problem-public")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(200)                                                  // 单节点 QPS 阈值上限
                .setIntervalSec(1);                                            // 统计时间窗口（秒）
        rules.add(problemPublicRule);

        // 针对登录与注册认证端点（高风险端点）防暴力破解
        GatewayFlowRule authRule = new GatewayFlowRule("user-auth")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(50)                                                   // 单节点 QPS 阈值上限
                .setIntervalSec(1);                                            // 统计时间窗口（秒）
        rules.add(authRule);

        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel default GatewayFlowRules loaded successfully, rule count: {}", rules.size());
    }
}
