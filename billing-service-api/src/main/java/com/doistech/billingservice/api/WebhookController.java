package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.WebhookReceivedResponse;
import com.doistech.billingservice.webhook.MercadoPagoWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Operation(summary = "Receber webhook do Mercado Pago",
            description = "Recebe notificacoes assincronas do Mercado Pago para atualizacao do estado das cobrancas e pagamentos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook recebido com sucesso.",
                    content = @Content(schema = @Schema(implementation = WebhookReceivedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido.")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Payload enviado pelo Mercado Pago.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Object.class),
                    examples = @ExampleObject(
                            name = "paymentWebhook",
                            value = "{\"action\":\"payment.updated\",\"data\":{\"id\":\"987654321\"},\"type\":\"payment\"}"
                    )
            )
    )
    public WebhookReceivedResponse mercadoPago(@RequestBody JsonNode payload) {
        mercadoPagoWebhookService.receive(payload);
        return new WebhookReceivedResponse("received");
    }
}
