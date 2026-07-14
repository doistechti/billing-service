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
                description = "API para cadastro de clientes, emissao de faturas, geracao de cobrancas, consulta de pagamentos e recebimento de webhooks.",
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
public class OpenApiConfig {
}
