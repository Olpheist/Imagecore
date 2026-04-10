package com.green.imagecore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

@Configuration
public class EcsConfig {

    @Value("${app.aws.region}")
    private String awsRegion;

    /**
     * AWS ECS client for dispatching analysis tool tasks via RunTask.
     * Region is read from app.aws.region (defaults to us-east-1 when AWS_REGION is not set).
     * Credentials use the default provider chain — task role in production, ~/.aws/credentials locally.
     */
    @Bean
    public EcsClient ecsClient() {
        return EcsClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
