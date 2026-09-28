package com.doistech.billingservice.config;

import com.doistech.billingservice.shared.crypto.SecretCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    SecretCipher secretCipher(ApiSecurityProperties properties) {
        return new SecretCipher(properties.encryptionKey());
    }
}
