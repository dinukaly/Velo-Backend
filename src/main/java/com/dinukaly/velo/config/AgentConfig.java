package com.dinukaly.velo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AgentConfig {

    /**
     * Bounded task executor for agent background runs.
     *
     * - corePoolSize: threads kept alive when idle
     * - maxPoolSize:  max concurrent agent executions across all projects
     * - queueCapacity: runs queued when all threads are busy
     * - threadNamePrefix: makes agent threads visible in logs/thread dumps
     *
     * These values are conservative for a single-instance deployment.
     * Tune via environment variables when scaling.
     */
    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("agent-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
