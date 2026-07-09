package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.WebhookReceivedResponse;
import com.doistech.billingservice.webhook.MercadoPagoWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final MercadoPagoWebhookService mercadoPagoWebhookService;

    public WebhookController(MercadoPagoWebhookService mercadoPagoWebhookService) {
        this.mercadoPagoWebhookService = mercadoPagoWebhookService;
    }

    @PostMapping("/mercado-pago")
    public WebhookReceivedResponse mercadoPago(@RequestBody JsonNode payload) {
        mercadoPagoWebhookService.receive(payload);
        return new WebhookReceivedResponse("received");
    }
}
