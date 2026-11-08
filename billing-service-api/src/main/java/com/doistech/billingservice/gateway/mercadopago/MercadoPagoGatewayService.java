package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.config.MercadoPagoProperties;
import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceItem;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.ChargeGatewayResponse;
import com.doistech.billingservice.gateway.DirectPaymentRequest;
import com.doistech.billingservice.gateway.GatewayAccount;
import com.doistech.billingservice.gateway.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.GatewayRefundResponse;
import com.doistech.billingservice.gateway.PaymentGatewayService;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MercadoPagoGatewayService implements PaymentGatewayService {

    @Override
    public Gateway gateway() {
        return Gateway.MERCADO_PAGO;
    }

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGatewayService.class);
    private static final DateTimeFormatter MP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestClient restClient;

    public MercadoPagoGatewayService(
            RestClient.Builder restClientBuilder,
            MercadoPagoProperties properties
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public ChargeGatewayResponse createCharge(MerchantCredentials credentials, Invoice invoice) {
        log.info(
                "creating mercado pago preference merchantId={} invoiceId={} sourceSystem={} externalReference={} amount={}",
                credentials.merchantId(),
                invoice.getId(),
                invoice.getSourceSystem(),
                invoice.getExternalReference(),
                invoice.getTotalAmount());

        Map<String, Object> payload = new HashMap<>();
        payload.put("external_reference", buildExternalReference(invoice));
        payload.put("notification_url", credentials.notificationUrl());
        payload.put("items", invoice.getItems().stream().map(this::toPreferenceItem).toList());
        payload.put("payer", Map.of("email", invoice.getCustomer().getEmail()));

        JsonNode response = send(credentials, "POST", "/checkout/preferences", payload, null);

        String preferenceId = text(response, "id");
        String paymentUrl = credentials.sandbox()
                ? firstNonBlank(text(response, "sandbox_init_point"), text(response, "init_point"))
                : text(response, "init_point");

        if (!StringUtils.hasText(preferenceId) || !StringUtils.hasText(paymentUrl)) {
            throw new GatewayIntegrationException("mercado pago did not return preference id or payment url");
        }

        log.info("mercado pago preference created invoiceId={} preferenceId={} sandbox={}",
                invoice.getId(), preferenceId, credentials.sandbox());

        return new ChargeGatewayResponse(
                Gateway.MERCADO_PAGO,
                preferenceId,
                paymentUrl,
                invoice.getTotalAmount(),
                ChargeStatus.WAITING_PAYMENT
        );
    }

    @Override
    public GatewayPaymentResponse createPayment(MerchantCredentials credentials, DirectPaymentRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transaction_amount", request.amount());
        payload.put("description", request.description());
        payload.put("external_reference", request.externalReference());
        if (isPublicUrl(credentials.notificationUrl())) {
            payload.put("notification_url", credentials.notificationUrl());
        }
        payload.put("payer", toPayer(request.payer(), request.method() == ChargeMethod.BOLETO));

        switch (request.method()) {
            case PIX -> payload.put("payment_method_id", "pix");
            case BOLETO -> payload.put("payment_method_id", "bolbradesco");
            case CREDIT_CARD -> {
                DirectPaymentRequest.Card card = request.card();
                payload.put("token", card.token());
                payload.put("payment_method_id", card.paymentMethodId());
                payload.put("installments", card.installments() == null ? 1 : card.installments());
                if (StringUtils.hasText(card.issuerId())) {
                    payload.put("issuer_id", card.issuerId());
                }
            }
            default -> throw new IllegalArgumentException("unsupported direct payment method " + request.method());
        }
        if (request.expiresAt() != null && request.method() != ChargeMethod.CREDIT_CARD) {
            payload.put("date_of_expiration", MP_DATE_TIME.format(request.expiresAt()));
        }

        log.info("creating mercado pago payment merchantId={} method={} externalReference={} amount={}",
                credentials.merchantId(), request.method(), request.externalReference(), request.amount());

        JsonNode response = send(credentials, "POST", "/v1/payments", payload, request.idempotencyKey());
        GatewayPaymentResponse payment = toPaymentResponse(response);
        log.info("mercado pago payment created paymentId={} status={} statusDetail={}",
                payment.gatewayPaymentId(), payment.gatewayStatus(), payment.statusDetail());
        return payment;
    }

    @Override
    public GatewayPaymentResponse getPayment(MerchantCredentials credentials, String gatewayPaymentId) {
        log.info("looking up mercado pago payment merchantId={} paymentId={}", credentials.merchantId(), gatewayPaymentId);
        JsonNode response = send(credentials, "GET", "/v1/payments/" + gatewayPaymentId, null, null);
        GatewayPaymentResponse payment = toPaymentResponse(response);
        log.info("mercado pago payment lookup succeeded paymentId={} status={} externalReference={}",
                payment.gatewayPaymentId(), payment.gatewayStatus(), payment.externalReference());
        return payment;
    }

    @Override
    public GatewayRefundResponse refund(
            MerchantCredentials credentials, String gatewayPaymentId, BigDecimal amount, String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        if (amount != null) {
            payload.put("amount", amount);
        }
        log.info("refunding mercado pago payment merchantId={} paymentId={} amount={}",
                credentials.merchantId(), gatewayPaymentId, amount);
        JsonNode response = send(credentials, "POST", "/v1/payments/" + gatewayPaymentId + "/refunds", payload, idempotencyKey);
        return new GatewayRefundResponse(text(response, "id"), text(response, "status"), decimal(response, "amount"));
    }

    @Override
    public GatewayPaymentResponse cancelPayment(MerchantCredentials credentials, String gatewayPaymentId) {
        log.info("cancelling mercado pago payment merchantId={} paymentId={}", credentials.merchantId(), gatewayPaymentId);
        JsonNode response = send(credentials, "PUT", "/v1/payments/" + gatewayPaymentId, Map.of("status", "cancelled"), null);
        return toPaymentResponse(response);
    }

    @Override
    public GatewayAccount verifyAccount(String accessToken) {
        MerchantCredentials probe = new MerchantCredentials(null, accessToken, null, null, false, null);
        JsonNode response = send(probe, "GET", "/users/me", null, null);
        return new GatewayAccount(text(response, "id"), text(response, "nickname"), text(response, "site_id"));
    }

    private GatewayPaymentResponse toPaymentResponse(JsonNode response) {
        JsonNode transactionData = response.path("point_of_interaction").path("transaction_data");
        JsonNode transactionDetails = response.path("transaction_details");
        String ticketUrl = firstNonBlank(
                text(transactionData, "ticket_url"),
                text(transactionDetails, "external_resource_url"));
        return new GatewayPaymentResponse(
                Gateway.MERCADO_PAGO,
                text(response, "id"),
                text(response, "status"),
                text(response, "status_detail"),
                decimal(response, "transaction_amount"),
                decimalOrZero(response, "transaction_amount_refunded"),
                parseDateTime(text(response, "date_approved")),
                text(response, "external_reference"),
                response.hasNonNull("installments") ? response.path("installments").asInt() : null,
                text(transactionData, "qr_code"),
                text(transactionData, "qr_code_base64"),
                ticketUrl,
                text(response.path("barcode"), "content"),
                text(transactionDetails, "digitable_line"),
                parseDateTime(text(response, "date_of_expiration")),
                response
        );
    }

    private Map<String, Object> toPayer(DirectPaymentRequest.Payer payer, boolean includeAddress) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("email", payer.email());
        putIfText(value, "first_name", payer.firstName());
        putIfText(value, "last_name", payer.lastName());
        if (StringUtils.hasText(payer.documentNumber())) {
            value.put("identification", Map.of("type", payer.documentType(), "number", payer.documentNumber()));
        }
        if (includeAddress && payer.address() != null) {
            DirectPaymentRequest.Address address = payer.address();
            Map<String, Object> mpAddress = new LinkedHashMap<>();
            putIfText(mpAddress, "zip_code", address.zipCode());
            putIfText(mpAddress, "street_name", address.street());
            putIfText(mpAddress, "street_number", address.number());
            putIfText(mpAddress, "neighborhood", address.neighborhood());
            putIfText(mpAddress, "city", address.city());
            putIfText(mpAddress, "federal_unit", address.state());
            value.put("address", mpAddress);
        }
        return value;
    }

    private Map<String, Object> toPreferenceItem(InvoiceItem item) {
        Map<String, Object> value = new HashMap<>();
        value.put("title", item.getDescription());
        value.put("description", item.getDescription());
        try {
            value.put("quantity", item.getQuantity().intValueExact());
        } catch (ArithmeticException exception) {
            throw new GatewayIntegrationException("checkout pro requires whole item quantities");
        }
        value.put("unit_price", item.getUnitAmount());
        value.put("currency_id", "BRL");
        return value;
    }

    public static String buildExternalReference(Invoice invoice) {
        return "INVOICE:%s|SOURCE:%s|REF:%s".formatted(
                invoice.getId(),
                invoice.getSourceSystem(),
                invoice.getExternalReference());
    }

    private JsonNode send(MerchantCredentials credentials, String method, String path, Object payload, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec request = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .headers(headers -> {
                        headers.setBearerAuth(credentials.accessToken());
                        if (!"GET".equals(method)) {
                            headers.set("X-Idempotency-Key", StringUtils.hasText(idempotencyKey)
                                    ? idempotencyKey
                                    : UUID.randomUUID().toString());
                        }
                    });
            if (payload != null) {
                request.body(payload);
            }
            JsonNode response = request.retrieve().body(JsonNode.class);
            if (response == null) {
                throw new GatewayIntegrationException("mercado pago returned empty response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("mercado pago returned not found method={} path={} body={}",
                        method, path, sanitizeBody(exception.getResponseBodyAsString()));
            } else {
                log.error("mercado pago request failed method={} path={} statusCode={} body={}",
                        method, path, exception.getStatusCode(), sanitizeBody(exception.getResponseBodyAsString()));
            }
            throw new GatewayIntegrationException("mercado pago request failed: " + mercadoPagoMessage(exception), exception);
        } catch (ResourceAccessException exception) {
            log.error("mercado pago unreachable method={} path={} error={}", method, path, exception.getMessage());
            throw new GatewayIntegrationException("mercado pago unreachable", exception);
        }
    }

    private String mercadoPagoMessage(RestClientResponseException exception) {
        try {
            JsonNode body = exception.getResponseBodyAs(JsonNode.class);
            String message = body == null ? null : text(body, "message");
            return StringUtils.hasText(message) ? message : exception.getStatusCode().toString();
        } catch (RuntimeException ignored) {
            return exception.getStatusCode().toString();
        }
    }

    private boolean isPublicUrl(String url) {
        return url != null && url.startsWith("https://");
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.decimalValue();
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String sanitizeBody(String value) {
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }
        return value.length() > 1000 ? value.substring(0, 1000) + "...(truncated)" : value;
    }
}
