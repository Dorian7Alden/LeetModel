package com.leetmodel.review.config;

import com.leetmodel.common.core.telemetry.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 正式评审与调度线程隔离配置。
 */
@Configuration(proxyBeanMethods = false)
public class ReviewWorkerConfiguration {

    /**
     * 创建无积压内存队列的有界评审执行器。
     *
     * @param properties Worker 配置
     * @return 评审执行器
     */
    @Bean(name = "reviewTaskExecutor")
    public ThreadPoolTaskExecutor reviewTaskExecutor(ReviewWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getConcurrency());
        executor.setMaxPoolSize(properties.getConcurrency());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("review-worker-");
        executor.setTaskDecorator(CorrelationTaskDecorator.INSTANCE);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
