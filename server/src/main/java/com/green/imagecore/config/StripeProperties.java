package com.green.imagecore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    private String secretKey;
    private String webhookSecret;
    private String proMonthlyPriceId;
    private String publicKey;
}