package com.doistech.billingservice;

import com.doistech.billingservice.config.ApiSecurityProperties;
import com.doistech.billingservice.config.CallbackProperties;
import com.doistech.billingservice.config.CorsProperties;
import com.doistech.billingservice.config.MercadoPagoProperties;
import com.doistech.billingservice.config.PagarMeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        MercadoPagoProperties.class,
        PagarMeProperties.class,
        ApiSecurityProperties.class,
        CorsProperties.class,
        CallbackProperties.class
})
public class BillingServiceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApiApplication.class, args);
    }
}
