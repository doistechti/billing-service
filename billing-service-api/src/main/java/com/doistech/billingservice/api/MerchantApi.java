package com.doistech.billingservice.api;

import com.doistech.billingservice.core.payment.Gateway;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public final class MerchantApi {

    private MerchantApi() {
    }

    @Schema(name = "CreateMerchantRequest", description = "Dados para cadastrar uma conta recebedora.")
    public record CreateMerchantRequest(
            @Schema(description = "Código único do merchant.", example = "store-aurea")
            @NotBlank(message = "code is required")
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,59}", message = "code must be lowercase letters, digits or hyphen")
            String code,
            @Schema(description = "Nome de exibição.", example = "Aurea Joias")
            @NotBlank(message = "name is required") @Size(max = 255) String name,
            @Schema(description = "Access token Mercado Pago do recebedor.", nullable = true)
            String mercadoPagoAccessToken,
            @Schema(description = "Public key Mercado Pago (tokenização de cartão no navegador).", nullable = true)
            String mercadoPagoPublicKey,
            @Schema(description = "Segredo de assinatura dos webhooks do Mercado Pago.", nullable = true)
            String mercadoPagoWebhookSecret,
            @Schema(description = "Usa sandbox_init_point no Checkout Pro.", nullable = true)
            Boolean sandbox,
            @Schema(description = "URL que recebe os callbacks de status.", example = "https://api.loja.com/webhooks/billing", nullable = true)
            @Pattern(regexp = "https?://.+", message = "callbackUrl must be an http(s) url") String callbackUrl,
            @Schema(description = "Gateway que o merchant usa para receber. Sem valor: MERCADO_PAGO. "
                    + "Credenciais Pagar.me são cadastradas pelo próprio merchant em PUT /api/merchant/pagar-me.", nullable = true)
            Gateway gateway
    ) {
    }

    @Schema(name = "UpdateMerchantRequest", description = "Campos nulos não são alterados. String vazia remove o valor.")
    public record UpdateMerchantRequest(
            @Size(max = 255) String name,
            String mercadoPagoAccessToken,
            String mercadoPagoPublicKey,
            String mercadoPagoWebhookSecret,
            Boolean sandbox,
            @Pattern(regexp = "(https?://.+)?", message = "callbackUrl must be an http(s) url") String callbackUrl,
            Boolean active
    ) {
    }

    @Schema(name = "MerchantResponse", description = "Merchant sem segredos.")
    public record MerchantResponse(
            UUID id,
            String code,
            String name,
            @Schema(description = "Início da API key, para identificação.", example = "bk_1a2b3c4d")
            String apiKeyPrefix,
            boolean mercadoPagoConfigured,
            String mercadoPagoPublicKey,
            boolean webhookSignatureEnabled,
            boolean sandbox,
            String callbackUrl,
            @Schema(description = "URL a cadastrar no painel do Mercado Pago como notificação de webhooks.")
            String mercadoPagoNotificationUrl,
            boolean active,
            LocalDateTime createdAt,
            Gateway gateway,
            boolean pagarMeConfigured,
            String pagarMePublicKey,
            @Schema(description = "URL de notificação do gateway do merchant (Mercado Pago ou Pagar.me).")
            String notificationUrl
    ) {
    }

    @Schema(name = "MerchantSecretsResponse", description = "Segredos exibidos uma única vez.")
    public record MerchantSecretsResponse(
            MerchantResponse merchant,
            @Schema(description = "API key a usar no header X-API-Key. Não é possível recuperá-la depois.")
            String apiKey,
            @Schema(description = "Segredo para validar X-Billing-Signature nos callbacks.")
            String callbackSecret
    ) {
    }

    @Schema(name = "MerchantPublicConfigResponse", description = "Configuração pública do merchant autenticado.")
    public record MerchantPublicConfigResponse(
            UUID id,
            String code,
            String name,
            String mercadoPagoPublicKey,
            boolean sandbox,
            boolean callbackConfigured,
            @Schema(description = "Access token Mercado Pago cadastrado.")
            boolean mercadoPagoConfigured,
            @Schema(description = "Webhooks do Mercado Pago são validados por assinatura.")
            boolean webhookSignatureEnabled,
            @Schema(description = "URL a cadastrar em Suas integrações → Webhooks no painel do Mercado Pago.")
            String mercadoPagoNotificationUrl,
            @Schema(description = "Conta dona do access token, verificada no cadastro.", nullable = true)
            String mercadoPagoAccountId,
            @Schema(nullable = true)
            String mercadoPagoAccountNickname,
            @Schema(nullable = true)
            LocalDateTime mercadoPagoVerifiedAt,
            @Schema(description = "Gateway do merchant. Define qual par de credenciais vale.")
            Gateway gateway,
            @Schema(description = "Secret key do Pagar.me cadastrada.")
            boolean pagarMeConfigured,
            @Schema(description = "Public key do Pagar.me (tokenização de cartão no navegador).", nullable = true)
            String pagarMePublicKey,
            @Schema(nullable = true)
            String pagarMeAccountName,
            @Schema(nullable = true)
            LocalDateTime pagarMeVerifiedAt,
            @Schema(description = "URL de notificação a cadastrar no painel do gateway do merchant.")
            String notificationUrl
    ) {
    }

    @Schema(name = "UpdateOwnMercadoPagoRequest",
            description = "Credenciais Mercado Pago do próprio merchant. accessToken/webhookSecret vazios mantêm o valor atual.")
    public record UpdateOwnMercadoPagoRequest(
            @Schema(description = "Access token (APP_USR-… produção ou TEST-… teste). Obrigatório no primeiro cadastro.", nullable = true)
            String accessToken,
            @Schema(description = "Public key correspondente.") @NotBlank(message = "publicKey is required") String publicKey,
            @Schema(description = "Assinatura secreta dos webhooks.", nullable = true)
            String webhookSecret,
            @Schema(description = "Sem valor: deduzido do prefixo TEST- do token.", nullable = true)
            Boolean sandbox
    ) {

        @Override
        public String toString() {
            return "UpdateOwnMercadoPagoRequest[publicKey=%s, sandbox=%s]".formatted(publicKey, sandbox);
        }
    }

    @Schema(name = "UpdateOwnPagarMeRequest",
            description = "Credenciais Pagar.me do próprio merchant. secretKey/webhookSecret vazios mantêm o valor atual.")
    public record UpdateOwnPagarMeRequest(
            @Schema(description = "Secret key (sk_test_… teste ou sk_… produção). Obrigatória no primeiro cadastro.", nullable = true)
            String secretKey,
            @Schema(description = "Public key correspondente (pk_test_… ou pk_…).") @NotBlank(message = "publicKey is required") String publicKey,
            @Schema(description = "Segredo que autentica os webhooks: use como senha do Basic auth da URL ou como ?token=.", nullable = true)
            String webhookSecret,
            @Schema(description = "Sem valor: deduzido do prefixo sk_test_ da secret key.", nullable = true)
            Boolean sandbox
    ) {

        @Override
        public String toString() {
            return "UpdateOwnPagarMeRequest[publicKey=%s, sandbox=%s]".formatted(publicKey, sandbox);
        }
    }
}
