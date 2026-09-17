package com.leetmodel.review.config;

import com.leetmodel.common.core.telemetry.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI评审 V3 并发线程池隔离配置。
 */
@Configuration(proxyBeanMethods = false)
public class ReviewV3Configuration {

    @Bean(name = "reviewSubTaskExecutor")
    public ThreadPoolTaskExecutor reviewSubTaskExecutor(ReviewV3Properties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getSubTaskCorePoolSize());
        executor.setMaxPoolSize(properties.getSubTaskMaxPoolSize());
        executor.setQueueCapacity(properties.getSubTaskQueueCapacity());
        executor.setThreadNamePrefix("review-v3-subtask-");
        executor.setTaskDecorator(CorrelationTaskDecorator.INSTANCE);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
