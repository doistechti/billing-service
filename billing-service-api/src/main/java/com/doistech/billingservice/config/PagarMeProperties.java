package com.doistech.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pagar-me")
public record PagarMeProperties(String baseUrl) {
}
