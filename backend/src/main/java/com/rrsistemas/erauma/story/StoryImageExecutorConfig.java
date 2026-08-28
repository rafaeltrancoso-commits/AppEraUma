package com.rrsistemas.erauma.story;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StoryImageExecutorConfig {
    @Bean
    Executor storyImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(6);
        executor.setThreadNamePrefix("story-image-");
        executor.initialize();
        return executor;
    }
}
