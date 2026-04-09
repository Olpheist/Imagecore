package com.green.imagecore.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.stripe")
public class StripeConfig {

    private String secretKey;
    private String webhookSecret;
    private String proMonthlyPriceId;
    private String publicKey;
    private String successUrl;
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}