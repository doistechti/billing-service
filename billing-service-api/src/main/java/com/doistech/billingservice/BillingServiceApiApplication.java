package com.doistech.billingservice;

import com.doistech.billingservice.config.ApiSecurityProperties;
import com.doistech.billingservice.config.CorsProperties;
import com.doistech.billingservice.config.MercadoPagoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MercadoPagoProperties.class, ApiSecurityProperties.class, CorsProperties.class})
public class BillingServiceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApiApplication.class, args);
    }
}
