package com.green.imagecore.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.concurrent.*;

@Configuration
public class S3Config {

    /**
     * Creates the AWS S3 client using the default credential provider chain.
     * In production (ECS), credentials are sourced automatically from the ECS task role.
     * In development, they are sourced from environment variables or ~/.aws/credentials.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder().build();
    }

    /**
     * Bounded thread pool for parallel DICOM file uploads to S3.
     * 8 concurrent threads; CallerRunsPolicy provides backpressure if the queue fills.
     */
    @Bean(destroyMethod = "shutdown")
    @Qualifier("dicomS3UploadExecutor")
    public ExecutorService dicomS3UploadExecutor() {
        return new ThreadPoolExecutor(
                8, 8,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
