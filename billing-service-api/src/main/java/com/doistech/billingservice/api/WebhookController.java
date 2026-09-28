package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.WebhookReceivedResponse;
import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.webhook.MercadoPagoWebhookService;
import com.doistech.billingservice.webhook.MercadoPagoWebhookService.WebhookHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Recebimento de notificacoes de provedores externos.")
public class WebhookController {

    private final MercadoPagoWebhookService mercadoPagoWebhookService;

    public WebhookController(MercadoPagoWebhookService mercadoPagoWebhookService) {
        this.mercadoPagoWebhookService = mercadoPagoWebhookService;
    }

    @PostMapping("/mercado-pago")
    @Operation(summary = "Receber webhook do Mercado Pago (merchant legacy)",
            description = "Rota mantida para a configuracao anterior; equivale a /mercado-pago/{legacyMerchantId}.")
    public WebhookReceivedResponse mercadoPagoLegacy(
            @RequestBody(required = false) JsonNode payload,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId) {
        return mercadoPago(Merchant.LEGACY_ID, payload, signature, requestId, dataId);
    }

    @PostMapping("/mercado-pago/{merchantId}")
    @Operation(summary = "Receber webhook do Mercado Pago",
            description = "Recebe notificacoes do Mercado Pago de um merchant. Valida x-signature quando o merchant tem webhook secret.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook recebido.",
                    content = @Content(schema = @Schema(implementation = WebhookReceivedResponse.class))),
            @ApiResponse(responseCode = "401", description = "Assinatura invalida."),
            @ApiResponse(responseCode = "404", description = "Merchant inexistente."),
            @ApiResponse(responseCode = "502", description = "Falha transitoria ao consultar o gateway; o Mercado Pago reenvia.")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Payload enviado pelo Mercado Pago.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Object.class),
                    examples = @ExampleObject(
                            name = "paymentWebhook",
                            value = "{\"action\":\"payment.updated\",\"data\":{\"id\":\"987654321\"},\"type\":\"payment\"}"
                    )
            )
    )
    public WebhookReceivedResponse mercadoPago(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) JsonNode payload,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId) {
        mercadoPagoWebhookService.receive(merchantId, payload, new WebhookHeaders(signature, requestId, dataId));
        return new WebhookReceivedResponse("received");
    }
}
