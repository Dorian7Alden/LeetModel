package com.leetmodel.gateway.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.core.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 响应式配置 —— JWT 无状态认证 + 路由鉴权规则。
 *
 * <p>关键设计决策：
 * <ul>
 *   <li><b>JWT 无状态模式</b>：Token 由签名自包含，校验不查 Redis Session。
 *       仅校验签名 + 过期时间，性能最优。</li>
 *   <li><b>白名单路径放行</b>：注册、登录无需携带 Token。</li>
 *   <li><b>其余路径全部拦截</b>：任何未在白名单中的路径，均需携带有效 JWT。</li>
 *   <li><b>当前未接入 Redis 黑名单</b>：登出后 Token 在过期前仍可通过网关。
 *       业务服务层（user 服务）会二次校验黑名单，不构成安全漏洞，
 *       但存在网关层放行已登出 Token 的已知限制。</li>
 * </ul>
 *
 * <p>⚠️ 注意：Gateway 基于 WebFlux 响应式架构，必须使用
 * {@code sa-token-reactor-spring-boot3-starter}，servlet 版无法启动。</p>
 *
 * @author LeetModel
 */
@Configuration
public class SaTokenConfig {

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${jwt.timeout:604800}")
    private long timeout;

    /**
     * 配置 StpLogic 为 JWT 无状态模式。
     *
     * <p>密钥必须与 user 服务保持一致，否则 Gateway 校验签名失败，
     * user 服务签发的 Token 会被网关拒绝。</p>
     */
    @Bean
    public StpLogic stpLogic() {
        StpLogicJwtForStateless stpLogic = new StpLogicJwtForStateless("login");

        cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
        config.setJwtSecretKey(jwtSecretKey);
        config.setTimeout(timeout);
        stpLogic.setConfig(config);

        return stpLogic;
    }

    /**
     * 注册 Sa-Token 响应式过滤器，定义路由鉴权规则。
     *
     * <p>规则：匹配所有请求 → 排除白名单 → 其余必须登录。</p>
     */
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单：登录和注册不校验 Token
                .addExclude(
                        "/api/auth/login",
                        "/api/auth/register"
                )
                // 鉴权规则：其余路径必须登录
                .setAuth(obj -> StpUtil.checkLogin())
                // 鉴权失败返回统一格式 JSON
                .setError(e -> Result.fail(40100, "未登录或 Token 已失效，请重新登录"));
    }
}
