package com.dinukaly.velo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AiCallLeaseConfig {
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService aiLeaseScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ai-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
    }
}
