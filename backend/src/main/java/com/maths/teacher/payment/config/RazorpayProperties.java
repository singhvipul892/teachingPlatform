package com.maths.teacher.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.razorpay")
public class RazorpayProperties {

    private String keyId;
    private String keySecret;
    private String currency = "INR";

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String keySecret) { this.keySecret = keySecret; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
