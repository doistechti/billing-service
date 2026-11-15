package com.doistech.billingservice.api;

import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingApi {

    private BillingApi() {
    }

    @Schema(name = "CreateCustomerRequest", description = "Dados para cadastrar um cliente no billing service.")
    public record CreateCustomerRequest(
            @Schema(description = "Sistema de origem do cliente.", example = "erp")
            @NotBlank(message = "sourceSystem is required") String sourceSystem,
            @Schema(description = "Identificador do cliente no sistema de origem.", example = "customer-123")
            @NotBlank(message = "externalReference is required") String externalReference,
            @Schema(description = "Nome completo ou razão social do cliente.", example = "Maria da Silva")
            @NotBlank(message = "name is required") String name,
            @Schema(description = "E-mail principal do cliente.", example = "maria@empresa.com")
            @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
            @Schema(description = "Documento do cliente.", example = "12345678901", nullable = true)
            String document,
            @Schema(description = "Telefone do cliente.", example = "+5511999999999", nullable = true)
            String phone
    ) {
    }

    @Schema(name = "CustomerResponse", description = "Cliente retornado pela API.")
    public record CustomerResponse(
            @Schema(description = "Identificador interno do cliente.", example = "8d56f5a6-fb5d-4d1c-a8f4-1831fd9f8d15")
            UUID id,
            @Schema(description = "Sistema de origem do cliente.", example = "erp")
            String sourceSystem,
            @Schema(description = "Identificador do cliente no sistema de origem.", example = "customer-123")
            String externalReference,
            @Schema(description = "Nome do cliente.", example = "Maria da Silva")
            String name,
            @Schema(description = "E-mail do cliente.", example = "maria@empresa.com")
            String email,
            @Schema(description = "Documento do cliente.", nullable = true)
            String document,
            @Schema(description = "Telefone do cliente.", nullable = true)
            String phone,
            @Schema(description = "Indica se o cliente está ativo.", example = "true")
            boolean active
    ) {
    }

    @Schema(name = "CreateInvoiceRequest", description = "Dados para criar uma fatura.")
    public record CreateInvoiceRequest(
            @Schema(description = "Identificador do cliente que receberá a fatura.", example = "8d56f5a6-fb5d-4d1c-a8f4-1831fd9f8d15")
            @NotNull(message = "customerId is required") UUID customerId,
            @Schema(description = "Sistema de origem da fatura.", example = "erp")
            @NotBlank(message = "sourceSystem is required") String sourceSystem,
            @Schema(description = "Identificador da fatura no sistema de origem.", example = "invoice-2026-0001")
            @NotBlank(message = "externalReference is required") String externalReference,
            @Schema(description = "Descrição resumida da cobrança.", example = "Mensalidade de julho/2026")
            @NotBlank(message = "description is required") String description,
            @Schema(description = "Data de vencimento da fatura.", example = "2026-07-31", nullable = true)
            LocalDate dueDate,
            @ArraySchema(schema = @Schema(implementation = CreateInvoiceItemRequest.class), minItems = 1,
                    arraySchema = @Schema(description = "Itens que compõem a fatura."))
            @NotEmpty(message = "items are required") List<@Valid CreateInvoiceItemRequest> items
    ) {
    }

    @Schema(name = "CreateInvoiceItemRequest", description = "Item da fatura.")
    public record CreateInvoiceItemRequest(
            @Schema(description = "Descrição do item.", example = "Plano Pro")
            @NotBlank(message = "item description is required") String description,
            @Schema(description = "Quantidade faturada.", example = "1")
            @NotNull(message = "item quantity is required") @Positive(message = "item quantity must be positive") BigDecimal quantity,
            @Schema(description = "Valor unitário do item.", example = "199.90")
            @NotNull(message = "item unitAmount is required") @Positive(message = "item unitAmount must be positive") BigDecimal unitAmount
    ) {
    }

    @Schema(name = "InvoiceItemResponse", description = "Item retornado na fatura.")
    public record InvoiceItemResponse(
            @Schema(description = "Identificador interno do item.", example = "0cc8b0d0-8fe1-4d72-94e5-77489f264bc4")
            UUID id,
            @Schema(description = "Descrição do item.", example = "Plano Pro")
            String description,
            @Schema(description = "Quantidade faturada.", example = "1")
            BigDecimal quantity,
            @Schema(description = "Valor unitário do item.", example = "199.90")
            BigDecimal unitAmount,
            @Schema(description = "Valor total do item.", example = "199.90")
            BigDecimal totalAmount
    ) {
    }

    @Schema(name = "InvoiceResponse", description = "Fatura retornada pela API.")
    public record InvoiceResponse(
            @Schema(description = "Identificador interno da fatura.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            UUID id,
            @Schema(description = "Cliente relacionado à fatura.", example = "8d56f5a6-fb5d-4d1c-a8f4-1831fd9f8d15")
            UUID customerId,
            @Schema(description = "Sistema de origem da fatura.", example = "erp")
            String sourceSystem,
            @Schema(description = "Identificador da fatura no sistema de origem.", example = "invoice-2026-0001")
            String externalReference,
            @Schema(description = "Descrição resumida da cobrança.", example = "Mensalidade de julho/2026")
            String description,
            @Schema(description = "Valor total da fatura.", example = "199.90")
            BigDecimal totalAmount,
            @Schema(description = "Data de vencimento.", example = "2026-07-31", nullable = true)
            LocalDate dueDate,
            @Schema(description = "Data/hora de pagamento, quando pago.", example = "2026-07-20T14:30:00", nullable = true)
            LocalDateTime paidAt,
            @Schema(description = "Data/hora de cancelamento, quando cancelado.", example = "2026-07-18T10:00:00", nullable = true)
            LocalDateTime canceledAt,
            @Schema(description = "Status atual da fatura.")
            InvoiceStatus status,
            @ArraySchema(schema = @Schema(implementation = InvoiceItemResponse.class),
                    arraySchema = @Schema(description = "Itens que compõem a fatura."))
            List<InvoiceItemResponse> items
    ) {
    }

    @Schema(name = "CreateChargeRequest", description = "Dados para criar uma cobrança em gateway.")
    public record CreateChargeRequest(
            @Schema(description = "Identificador da fatura a ser cobrada.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            @NotNull(message = "invoiceId is required") UUID invoiceId,
            @Schema(description = "Gateway de pagamento a ser usado.")
            @NotNull(message = "gateway is required") Gateway gateway,
            @Schema(description = "Forma de pagamento. Sem valor, gera Checkout Pro (paymentUrl).", nullable = true)
            ChargeMethod method,
            @Schema(description = "Dados do pagador. Obrigatório para PIX, CREDIT_CARD e BOLETO.", nullable = true)
            @Valid PayerRequest payer,
            @Schema(description = "Cartão tokenizado no navegador. Obrigatório para CREDIT_CARD.", nullable = true)
            @Valid CardRequest card,
            @Schema(description = "Validade em minutos para PIX (padrão 30) e BOLETO (padrão 3 dias).", example = "30", nullable = true)
            @Positive(message = "expiresInMinutes must be positive") Integer expiresInMinutes
    ) {

        public CreateChargeRequest(UUID invoiceId, Gateway gateway) {
            this(invoiceId, gateway, null, null, null, null);
        }

        public ChargeMethod methodOrDefault() {
            return method == null ? ChargeMethod.CHECKOUT_PRO : method;
        }
    }

    @Schema(name = "PayerRequest", description = "Pagador da cobrança. Campos ausentes usam os dados do customer.")
    public record PayerRequest(
            @Schema(description = "Nome completo.", example = "Pedro Almeida", nullable = true) String name,
            @Schema(description = "E-mail.", example = "pedro@email.com", nullable = true)
            @Email(message = "payer email must be valid") String email,
            @Schema(description = "CPF ou CNPJ, apenas dígitos.", example = "12345678909", nullable = true)
            @Pattern(regexp = "\\d{11}|\\d{14}", message = "payer document must have 11 or 14 digits") String document,
            @Schema(description = "Endereço. Obrigatório para BOLETO.", nullable = true)
            @Valid AddressRequest address,
            @Schema(description = "Telefone com DDD, apenas dígitos (10 a 13, com ou sem 55). Obrigatório no Pagar.me; ausente usa o do customer.",
                    example = "21991017888", nullable = true)
            @Pattern(regexp = "\\d{10,13}", message = "payer phone must have 10 to 13 digits") String phone
    ) {

        /** Compatibilidade: pagador sem telefone. */
        public PayerRequest(String name, String email, String document, AddressRequest address) {
            this(name, email, document, address, null);
        }
    }

    @Schema(name = "AddressRequest", description = "Endereço do pagador.")
    public record AddressRequest(
            @NotBlank(message = "address zipCode is required") @Pattern(regexp = "\\d{8}", message = "address zipCode must have 8 digits") String zipCode,
            @NotBlank(message = "address street is required") String street,
            @NotBlank(message = "address number is required") String number,
            @NotBlank(message = "address neighborhood is required") String neighborhood,
            @NotBlank(message = "address city is required") String city,
            @NotBlank(message = "address state is required") @Pattern(regexp = "[A-Z]{2}", message = "address state must be a 2-letter UF") String state
    ) {
    }

    @Schema(name = "CardRequest", description = "Cartão tokenizado pelo MP.js com a public key do merchant.")
    public record CardRequest(
            @Schema(description = "Token do cartão (uso único).") @NotBlank(message = "card token is required") String token,
            @Schema(description = "Bandeira retornada pelo MP.js.", example = "visa") @NotBlank(message = "card paymentMethodId is required") String paymentMethodId,
            @Schema(description = "Emissor retornado pelo MP.js.", nullable = true) String issuerId,
            @Schema(description = "Número de parcelas.", example = "10") @Positive(message = "card installments must be positive") Integer installments
    ) {

        @Override
        public String toString() {
            return "CardRequest[paymentMethodId=%s, installments=%s]".formatted(paymentMethodId, installments);
        }
    }

    @Schema(name = "RefundChargeRequest", description = "Estorno de uma cobrança paga.")
    public record RefundChargeRequest(
            @Schema(description = "Valor a estornar. Sem valor, estorna o saldo total.", example = "50.00", nullable = true)
            @Positive(message = "amount must be positive") BigDecimal amount
    ) {
    }

    @Schema(name = "ChargeResponse", description = "Cobrança retornada pela API.")
    public record ChargeResponse(
            @Schema(description = "Identificador interno da cobrança.", example = "e05a5fd0-48dd-4d6c-8f2a-3d7a6af88bde")
            UUID id,
            @Schema(description = "Fatura associada à cobrança.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            UUID invoiceId,
            @Schema(description = "Gateway usado para gerar a cobrança.")
            Gateway gateway,
            @Schema(description = "Identificador da preferência/transação no gateway.", example = "123456789")
            String gatewayPreferenceId,
            @Schema(description = "Identificador do pagamento no gateway, quando existir.", example = "987654321")
            String gatewayPaymentId,
            @Schema(description = "URL para o usuário concluir o pagamento (Checkout Pro).", example = "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123")
            String paymentUrl,
            @Schema(description = "Status atual da cobrança.")
            ChargeStatus status,
            @Schema(description = "Valor da cobrança.", example = "199.90")
            BigDecimal amount,
            @Schema(description = "Forma de pagamento.")
            ChargeMethod method,
            @Schema(description = "Detalhe do status no gateway (ex.: cc_rejected_insufficient_amount).", nullable = true)
            String statusDetail,
            @Schema(description = "Parcelas do cartão.", nullable = true)
            Integer installments,
            @Schema(description = "Dados do Pix, quando method = PIX.", nullable = true)
            PixResponse pix,
            @Schema(description = "Dados do boleto, quando method = BOLETO.", nullable = true)
            BoletoResponse boleto,
            @Schema(description = "Validade do Pix/boleto.", nullable = true)
            LocalDateTime expiresAt,
            @Schema(description = "Data/hora da aprovação.", nullable = true)
            LocalDateTime paidAt,
            @Schema(description = "Valor já estornado.", example = "0.00")
            BigDecimal refundedAmount,
            @Schema(description = "Criação da cobrança.")
            LocalDateTime createdAt
    ) {
    }

    @Schema(name = "PixResponse", description = "Dados para pagamento via Pix.")
    public record PixResponse(
            @Schema(description = "Código copia e cola.") String qrCode,
            @Schema(description = "Imagem PNG do QR Code em Base64.") String qrCodeBase64,
            @Schema(description = "Página do Mercado Pago com o QR Code.") String ticketUrl
    ) {
    }

    @Schema(name = "BoletoResponse", description = "Dados para pagamento via boleto.")
    public record BoletoResponse(
            @Schema(description = "Código de barras.") String barcode,
            @Schema(description = "Linha digitável.") String digitableLine,
            @Schema(description = "URL do PDF do boleto.") String pdfUrl
    ) {
    }

    @Schema(name = "PaymentResponse", description = "Pagamento retornado pela API.")
    public record PaymentResponse(
            @Schema(description = "Identificador interno do pagamento.", example = "65ca0d73-c3eb-4d3d-a93d-8f298c3d2c51")
            UUID id,
            @Schema(description = "Fatura associada ao pagamento.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            UUID invoiceId,
            @Schema(description = "Cobrança associada ao pagamento.", example = "e05a5fd0-48dd-4d6c-8f2a-3d7a6af88bde")
            UUID chargeId,
            @Schema(description = "Gateway que processou o pagamento.")
            Gateway gateway,
            @Schema(description = "Identificador do pagamento no gateway.", example = "987654321")
            String gatewayPaymentId,
            @Schema(description = "Status retornado pelo gateway.", example = "approved")
            String gatewayStatus,
            @Schema(description = "Valor pago.", example = "199.90")
            BigDecimal amount,
            @Schema(description = "Data/hora em que o pagamento foi reconhecido.", example = "2026-07-20T14:30:00")
            LocalDateTime paidAt
    ) {
    }

    @Schema(name = "HealthResponse", description = "Resposta de saúde do serviço.")
    public record HealthResponse(String status, String service) {
    }

    @Schema(name = "WebhookReceivedResponse", description = "Confirmação de recebimento do webhook.")
    public record WebhookReceivedResponse(String status) {
    }

    @Schema(name = "CallbackDeliveryResponse", description = "Callback enviado ou pendente para o sistema cliente.")
    public record CallbackDeliveryResponse(
            UUID id,
            String eventType,
            UUID chargeId,
            String status,
            int attempts,
            LocalDateTime nextAttemptAt,
            Integer lastStatusCode,
            String lastError,
            LocalDateTime deliveredAt,
            LocalDateTime createdAt
    ) {
    }
}
