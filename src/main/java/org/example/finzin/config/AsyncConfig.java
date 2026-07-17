package org.example.finzin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded executor for @Async work (currently just RAG document indexing) so a burst of
 * create/update/delete calls can't spawn unbounded threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "indexingTaskExecutor")
    public Executor indexingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("indexing-");
        executor.initialize();
        return executor;
    }
}
