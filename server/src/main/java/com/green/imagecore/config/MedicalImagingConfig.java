package com.green.imagecore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;

@Configuration
public class MedicalImagingConfig {

    /**
     * Creates the AWS HealthImaging client using the default credential provider chain.
     * In production, credentials are sourced from the ECS task role.
     * In development, they are sourced from environment variables or ~/.aws/credentials.
     */
    @Bean
    public MedicalImagingClient medicalImagingClient() {
        return MedicalImagingClient.builder().build();
    }
}
