package com.leetmodel.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 审计消费只装配专用编解码器，不启用 common-messaging 的业务 Outbox。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class AuditMessagingConfiguration {

    @Bean
    OperationAuditMessageCodec operationAuditMessageCodec(
            ObjectMapper objectMapper,
            @Value("${leetmodel.audit.max-payload-bytes:65536}") int maxPayloadBytes) {
        return new OperationAuditMessageCodec(objectMapper, maxPayloadBytes);
    }
}
