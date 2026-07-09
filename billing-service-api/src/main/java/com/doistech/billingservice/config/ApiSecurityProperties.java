package com.doistech.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record ApiSecurityProperties(
        String apiKeyHeader,
        String apiKey
) {
}
