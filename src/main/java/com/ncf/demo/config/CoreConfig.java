package com.ncf.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CoreConfig {
    @Bean
    public ThreadPoolTaskExecutor mqttMessageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("mqtt-message-");
        executor.initialize();
        return executor;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
