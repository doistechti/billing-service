package com.doistech.billingservice.core.merchant;

import com.doistech.billingservice.config.ApiSecurityProperties;
import com.doistech.billingservice.config.MercadoPagoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mantém os sistemas clientes anteriores funcionando: a INTERNAL_API_KEY e as credenciais MERCADO_PAGO_* do
 * ambiente passam a pertencer ao merchant legacy.
 */
@Component
public class LegacyMerchantInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMerchantInitializer.class);

    private final MerchantService merchantService;
    private final ApiSecurityProperties securityProperties;
    private final MercadoPagoProperties mercadoPagoProperties;

    public LegacyMerchantInitializer(
            MerchantService merchantService,
            ApiSecurityProperties securityProperties,
            MercadoPagoProperties mercadoPagoProperties
    ) {
        this.merchantService = merchantService;
        this.securityProperties = securityProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        merchantService.syncLegacy(
                securityProperties.apiKey(),
                mercadoPagoProperties.accessToken(),
                mercadoPagoProperties.publicKey(),
                mercadoPagoProperties.webhookSecret(),
                mercadoPagoProperties.sandbox());
        log.info(
                "legacy merchant synchronized apiKeyConfigured={} mercadoPagoConfigured={} sandbox={}",
                StringUtils.hasText(securityProperties.apiKey()),
                StringUtils.hasText(mercadoPagoProperties.accessToken()),
                mercadoPagoProperties.sandbox());
    }
}
