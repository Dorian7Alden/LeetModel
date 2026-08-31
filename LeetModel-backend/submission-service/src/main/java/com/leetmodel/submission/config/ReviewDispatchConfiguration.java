package com.leetmodel.submission.config;

import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.config.MessagingProperties;
import com.leetmodel.submission.messaging.FeignReviewTaskPublisher;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 评审任务传输方式的互斥装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class ReviewDispatchConfiguration {

    /**
     * FEIGN_RELAY 复用公共 Outbox Relay，只替换传输端口。
     *
     * @param codec 消息编解码器
     * @param reviewFeignClient 评审内部客户端
     * @return Feign 传输端口
     */
    @Bean
    @ConditionalOnProperty(prefix = "submission.review", name = "transport", havingValue = "FEIGN_RELAY")
    public MessagePublisher feignReviewTaskPublisher(
            MessageCodec codec,
            ReviewFeignClient reviewFeignClient
    ) {
        return new FeignReviewTaskPublisher(codec, reviewFeignClient);
    }

    /**
     * 启动时拒绝双主或无 Relay 的危险组合。
     *
     * @param dispatchProperties 业务传输配置
     * @param messagingProperties 公共 Relay 配置
     * @return 启动校验器
     */
    @Bean
    public InitializingBean reviewTransportGuard(
            ReviewDispatchProperties dispatchProperties,
            MessagingProperties messagingProperties
    ) {
        return () -> {
            boolean relayEnabled = messagingProperties.getRelay().isEnabled();
            boolean legacy = dispatchProperties.getTransport()
                    == ReviewDispatchProperties.Transport.LEGACY_FEIGN;
            if (legacy == relayEnabled) {
                throw new IllegalStateException(
                        "LEGACY_FEIGN 必须关闭 Outbox Relay；MQ_PRIMARY/FEIGN_RELAY 必须启用 Relay");
            }
        };
    }
}
