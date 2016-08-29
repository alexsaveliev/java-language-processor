package com.sourcegraph.langp.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class TaskExecutorConfiguration {

    @Value("${tasks.pool.core.size:50}")
    private int corePoolSize;

    @Value("${tasks.pool.max.size:100}")
    private int maxPoolSize;

    @Bean
    public ExecutorService taskExecutor() {
        return new ThreadPoolExecutor(corePoolSize,
                maxPoolSize,
                Integer.MAX_VALUE,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
    }

}
