package com.leetmodel.admin.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** 为 admin → audit 内部查询注入独立服务令牌；不复用用户 Token。 */
public class AuditFeignConfig {
    @Bean
    RequestInterceptor auditInternalTokenInterceptor(
            @Value("${AUDIT_INTERNAL_TOKEN:}") String token) {
        return template -> {
            if (token != null && !token.isBlank()) {
                template.header("X-LeetModel-Audit-Token", token);
            }
        };
    }
}
