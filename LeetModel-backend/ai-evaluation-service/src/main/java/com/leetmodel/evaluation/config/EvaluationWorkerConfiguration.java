package com.leetmodel.evaluation.config;

import com.leetmodel.common.core.telemetry.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class EvaluationWorkerConfiguration {

    @Bean("evaluationTaskExecutor")
    public ThreadPoolTaskExecutor evaluationTaskExecutor(EvaluationWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("evaluation-worker-");
        executor.setCorePoolSize(properties.getConcurrency());
        executor.setMaxPoolSize(properties.getConcurrency());
        executor.setQueueCapacity(0);
        executor.setTaskDecorator(CorrelationTaskDecorator.INSTANCE);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
