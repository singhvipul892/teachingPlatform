package com.maths.teacher.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayConfig {

    @Bean
    public RazorpayClient razorpayClient(RazorpayProperties properties) {
        if (properties.getKeyId() == null || properties.getKeyId().isBlank()) {
            throw new IllegalStateException("Razorpay key-id is required (RAZORPAY_KEY_ID env).");
        }
        if (properties.getKeySecret() == null || properties.getKeySecret().isBlank()) {
            throw new IllegalStateException("Razorpay key-secret is required (RAZORPAY_KEY_SECRET env).");
        }
        try {
            return new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialise Razorpay client: " + e.getMessage(), e);
        }
    }
}
