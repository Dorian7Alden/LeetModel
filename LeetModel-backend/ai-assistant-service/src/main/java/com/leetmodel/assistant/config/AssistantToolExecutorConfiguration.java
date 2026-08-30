package com.leetmodel.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 客服只读工具的独立有界执行池。 */
@Configuration
public class AssistantToolExecutorConfiguration {

    /** 创建支持中断和应用关闭等待的工具执行器。 */
    @Bean(name = "assistantToolExecutor")
    public ThreadPoolTaskExecutor assistantToolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("assistant-tool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
