package com.doistech.billingservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.callbacks")
public record CallbackProperties(
        boolean enabled,
        int maxAttempts,
        int batchSize,
        Duration initialBackoff,
        Duration timeout
) {
}
