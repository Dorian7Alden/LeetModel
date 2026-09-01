package com.leetmodel.ranking.config;

import com.leetmodel.common.core.telemetry.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 排行重建与调度线程隔离。 */
@Configuration(proxyBeanMethods = false)
public class RankingRebuildExecutorConfiguration {
    @Bean(name = "rankingRebuildExecutor")
    public ThreadPoolTaskExecutor rankingRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ranking-rebuild-");
        executor.setTaskDecorator(CorrelationTaskDecorator.INSTANCE);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
