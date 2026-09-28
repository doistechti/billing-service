package com.doistech.billingservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Billing Service API",
                version = "v1",
                description = "API multi-merchant para clientes, faturas, cobrancas (Checkout Pro, Pix, cartao e boleto), estornos, webhooks do Mercado Pago e callbacks assinados.",
                contact = @Contact(name = "DOISTECH"),
                license = @License(name = "Proprietary")
        )
)
@SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "Chave de autenticacao exigida nos endpoints protegidos."
)
@SecurityScheme(
        name = "adminKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Admin-Key",
        description = "Chave de administracao (ADMIN_API_KEY) para cadastro de merchants."
)
public class OpenApiConfig {
}
