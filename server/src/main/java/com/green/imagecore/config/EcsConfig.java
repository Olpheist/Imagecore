package com.green.imagecore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ecs.EcsClient;

@Configuration
public class EcsConfig {

    /**
     * AWS ECS client for dispatching analysis tool tasks via RunTask.
     * Uses the default credential provider chain — in production (ECS), credentials
     * come from the task role. Locally, they come from ~/.aws/credentials or env vars.
     */
    @Bean
    public EcsClient ecsClient() {
        return EcsClient.builder().build();
    }
}
