package com.leetmodel.suggestion.config;

import com.leetmodel.common.core.telemetry.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class SuggestionWorkerConfiguration {

    @Bean(name = "suggestionTaskExecutor")
    public ThreadPoolTaskExecutor suggestionTaskExecutor(SuggestionWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getConcurrency());
        executor.setMaxPoolSize(properties.getConcurrency());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("suggestion-worker-");
        executor.setTaskDecorator(CorrelationTaskDecorator.INSTANCE);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
