package com.doistech.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercado-pago")
public record MercadoPagoProperties(
        String baseUrl,
        String accessToken,
        String publicKey,
        String webhookSecret,
        String notificationUrl
) {
}
