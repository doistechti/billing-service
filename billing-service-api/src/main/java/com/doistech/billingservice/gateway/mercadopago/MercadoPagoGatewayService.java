package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.config.MercadoPagoProperties;
import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceItem;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MercadoPagoGatewayService implements PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGatewayService.class);

    private final RestClient restClient;
    private final MercadoPagoProperties properties;

    public MercadoPagoGatewayService(
            RestClient.Builder restClientBuilder,
            MercadoPagoProperties properties
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public ChargeGatewayResponse createCharge(Invoice invoice) {
        ensureConfigured();

        Map<String, Object> payload = new HashMap<>();
        payload.put("external_reference", buildExternalReference(invoice));
        payload.put("notification_url", properties.notificationUrl());
        payload.put("items", invoice.getItems().stream().map(this::toPreferenceItem).toList());

        Map<String, Object> payer = new HashMap<>();
        payer.put("email", invoice.getCustomer().getEmail());
        payload.put("payer", payer);

        Map<String, Object> response = post("/checkout/preferences", payload);

        String preferenceId = stringValue(response.get("id"));
        String paymentUrl = firstNonBlank(
                stringValue(response.get("sandbox_init_point")),
                stringValue(response.get("init_point")));

        if (!StringUtils.hasText(preferenceId) || !StringUtils.hasText(paymentUrl)) {
            throw new GatewayIntegrationException("mercado pago did not return preference id or payment url");
        }

        return new ChargeGatewayResponse(
                Gateway.MERCADO_PAGO,
                preferenceId,
                paymentUrl,
                invoice.getTotalAmount(),
                ChargeStatus.WAITING_PAYMENT
        );
    }

    @Override
    public GatewayPaymentResponse getPayment(String gatewayPaymentId) {
        ensureConfigured();
        try {
            JsonNode response = restClient.get()
                    .uri("/v1/payments/{id}", gatewayPaymentId)
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, rawResponse) -> {
                        throw new GatewayIntegrationException("mercado pago payment lookup failed");
                    })
                    .body(JsonNode.class);

            if (response == null) {
                throw new GatewayIntegrationException("mercado pago returned empty payment response");
            }

            return new GatewayPaymentResponse(
                    Gateway.MERCADO_PAGO,
                    response.path("id").asText(),
                    response.path("status").asText(),
                    response.path("transaction_amount").decimalValue(),
                    parseDateTime(response.path("date_approved").asText(null)),
                    response.path("external_reference").asText(null),
                    response
            );
        } catch (RestClientResponseException exception) {
            log.error("mercado pago payment lookup failed: {}", exception.getResponseBodyAsString(), exception);
            throw new GatewayIntegrationException("mercado pago payment lookup failed", exception);
        }
    }

    private Map<String, Object> toPreferenceItem(InvoiceItem item) {
        Map<String, Object> value = new HashMap<>();
        value.put("title", item.getDescription());
        value.put("description", item.getDescription());
        value.put("quantity", item.getQuantity().intValueExact());
        value.put("unit_price", item.getUnitAmount());
        value.put("currency_id", "BRL");
        return value;
    }

    private String buildExternalReference(Invoice invoice) {
        return "INVOICE:%s|SOURCE:%s|REF:%s".formatted(
                invoice.getId(),
                invoice.getSourceSystem(),
                invoice.getExternalReference());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> payload) {
        try {
            return restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, rawResponse) -> {
                        throw new GatewayIntegrationException("mercado pago charge creation failed");
                    })
                    .body(Map.class);
        } catch (RestClientResponseException exception) {
            log.error("mercado pago charge creation failed: {}", exception.getResponseBodyAsString(), exception);
            throw new GatewayIntegrationException("mercado pago charge creation failed", exception);
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.accessToken())) {
            throw new GatewayIntegrationException("mercado pago access token is not configured");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
