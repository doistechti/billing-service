package com.doistech.billingservice.gateway.pagarme;

import com.doistech.billingservice.config.PagarMeProperties;
import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.invoice.Invoice;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gateway Pagar.me (API v5). Autenticação HTTP Basic com a secret key do merchant; valores em centavos.
 *
 * <p>{@code gatewayPaymentId} é o id da <em>charge</em> ({@code ch_...}). Pix, boleto e cartão criam um order
 * com um pagamento e usamos a primeira charge devolvida.
 *
 * <p>Detalhes ainda a confirmar na documentação oficial estão listados nas pendências do SDD 10.
 */
@Service
public class PagarMeGatewayService implements PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PagarMeGatewayService.class);
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final long MIN_PIX_SECONDS = 60;
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private final RestClient restClient;

    public PagarMeGatewayService(RestClient.Builder restClientBuilder, PagarMeProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public Gateway gateway() {
        return Gateway.PAGAR_ME;
    }

    /**
     * O link hospedado do Pagar.me ainda não é suportado: falta definir como reconciliar o pedido gerado
     * pelo link com a invoice (SDD 10, pendências). Use PIX, BOLETO ou CREDIT_CARD.
     */
    @Override
    public ChargeGatewayResponse createCharge(MerchantCredentials credentials, Invoice invoice) {
        throw new GatewayIntegrationException(
                "pagar.me does not support hosted checkout yet; use PIX, BOLETO or CREDIT_CARD");
    }

    @Override
    public GatewayPaymentResponse createPayment(MerchantCredentials credentials, DirectPaymentRequest request) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("closed", true);
        order.put("items", List.of(Map.of(
                "amount", toCents(request.amount()),
                "description", StringUtils.hasText(request.description()) ? request.description() : "Cobrança",
                "quantity", 1,
                "code", "1")));
        order.put("customer", toCustomer(request.payer(), request.method() == ChargeMethod.BOLETO));
        order.put("payments", List.of(toPayment(request)));
        // O external reference do billing passa de 52 caracteres (limite do campo code), então vai em metadata.
        if (StringUtils.hasText(request.externalReference())) {
            order.put("metadata", Map.of("external_reference", request.externalReference()));
        }

        log.info("creating pagar.me order merchantId={} method={} amount={}",
                credentials.merchantId(), request.method(), request.amount());
        JsonNode response = send(credentials, HttpMethod.POST, "/orders", order, request.idempotencyKey());

        JsonNode charge = response.path("charges").path(0);
        if (charge.isMissingNode() || charge.isNull()) {
            throw new GatewayIntegrationException("pagar.me did not return a charge for the order");
        }
        GatewayPaymentResponse payment = toPaymentResponse(charge, response);
        log.info("pagar.me charge created chargeId={} status={} statusDetail={}",
                payment.gatewayPaymentId(), payment.gatewayStatus(), payment.statusDetail());
        if ("failed".equalsIgnoreCase(payment.gatewayStatus())) {
            // Motivo da recusa (gateway_response.errors); sem dados de cartão, só mensagens do Pagar.me.
            log.warn("pagar.me charge failed chargeId={} gatewayResponse={}", payment.gatewayPaymentId(),
                    charge.path("last_transaction").path("gateway_response").toString());
        }
        return payment;
    }

    @Override
    public GatewayPaymentResponse getPayment(MerchantCredentials credentials, String gatewayPaymentId) {
        log.info("looking up pagar.me charge merchantId={} chargeId={}", credentials.merchantId(), gatewayPaymentId);
        JsonNode charge = send(credentials, HttpMethod.GET, "/charges/" + gatewayPaymentId, null, null);
        return toPaymentResponse(charge, charge.path("order"));
    }

    @Override
    public GatewayRefundResponse refund(
            MerchantCredentials credentials, String gatewayPaymentId, BigDecimal amount, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (amount != null) {
            payload.put("amount", toCents(amount));
        }
        log.info("refunding pagar.me charge merchantId={} chargeId={} amount={}",
                credentials.merchantId(), gatewayPaymentId, amount);
        JsonNode charge = send(credentials, HttpMethod.DELETE, "/charges/" + gatewayPaymentId, payload, idempotencyKey);
        BigDecimal refunded = amount != null ? amount : fromCentsOrNull(charge.path("canceled_amount"));
        return new GatewayRefundResponse(text(charge, "id"), text(charge, "status"), refunded);
    }

    @Override
    public GatewayPaymentResponse cancelPayment(MerchantCredentials credentials, String gatewayPaymentId) {
        log.info("cancelling pagar.me charge merchantId={} chargeId={}", credentials.merchantId(), gatewayPaymentId);
        JsonNode charge = send(credentials, HttpMethod.DELETE, "/charges/" + gatewayPaymentId, Map.of(), null);
        return toPaymentResponse(charge, charge.path("order"));
    }

    /**
     * A v5 não tem um "quem sou eu" para chave de conta: uma listagem mínima autenticada prova que a secret key
     * é válida (chave inválida → 401, erro não transitório). Sem id/nome da conta.
     */
    @Override
    public GatewayAccount verifyAccount(String secretKey) {
        MerchantCredentials probe = new MerchantCredentials(null, secretKey, null, null, false, null);
        send(probe, HttpMethod.GET, "/customers?page=1&size=1", null, null);
        return new GatewayAccount(null, null, null);
    }

    private Map<String, Object> toPayment(DirectPaymentRequest request) {
        Map<String, Object> payment = new LinkedHashMap<>();
        switch (request.method()) {
            case PIX -> {
                payment.put("payment_method", "pix");
                Map<String, Object> pix = new LinkedHashMap<>();
                if (request.expiresAt() != null) {
                    long seconds = Duration.between(OffsetDateTime.now(), request.expiresAt()).getSeconds();
                    pix.put("expires_in", Math.max(seconds, MIN_PIX_SECONDS));
                }
                payment.put("pix", pix);
            }
            case BOLETO -> {
                payment.put("payment_method", "boleto");
                Map<String, Object> boleto = new LinkedHashMap<>();
                boleto.put("instructions", "Pagar até o vencimento");
                if (request.expiresAt() != null) {
                    boleto.put("due_at", ISO_UTC.format(request.expiresAt().withOffsetSameInstant(ZoneOffset.UTC)));
                }
                payment.put("boleto", boleto);
            }
            case CREDIT_CARD -> {
                DirectPaymentRequest.Card card = request.card();
                payment.put("payment_method", "credit_card");
                Map<String, Object> creditCard = new LinkedHashMap<>();
                creditCard.put("installments", card.installments() == null ? 1 : card.installments());
                creditCard.put("card_token", card.token());
                payment.put("credit_card", creditCard);
            }
            default -> throw new IllegalArgumentException("unsupported direct payment method " + request.method());
        }
        return payment;
    }

    private Map<String, Object> toCustomer(DirectPaymentRequest.Payer payer, boolean includeAddress) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", fullName(payer));
        customer.put("email", payer.email());
        String document = digitsOnly(payer.documentNumber());
        if (StringUtils.hasText(document)) {
            boolean company = document.length() > 11;
            customer.put("document", document);
            customer.put("document_type", company ? "CNPJ" : "CPF");
            customer.put("type", company ? "company" : "individual");
        }
        Map<String, Object> phones = phones(payer.phone());
        if (phones != null) {
            customer.put("phones", phones);
        }
        if (includeAddress && payer.address() != null) {
            DirectPaymentRequest.Address address = payer.address();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("line_1", joinNonBlank(", ", address.number(), address.street(), address.neighborhood()));
            value.put("zip_code", digitsOnly(address.zipCode()));
            value.put("city", address.city());
            value.put("state", address.state());
            value.put("country", "BR");
            customer.put("address", value);
        }
        return customer;
    }

    /**
     * Telefone do pagador no formato da v5 ({@code phones.mobile_phone}): o Pagar.me recusa a cobrança sem ao menos um
     * ("At least one customer phone is required."). Aceita 10 a 13 dígitos, com ou sem o 55 do país.
     */
    static Map<String, Object> phones(String raw) {
        String digits = digitsOnly(raw);
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        if (digits.length() > 11 && digits.startsWith("55")) {
            digits = digits.substring(2);
        }
        if (digits.length() < 10 || digits.length() > 11) {
            return null;
        }
        Map<String, Object> mobile = new LinkedHashMap<>();
        mobile.put("country_code", "55");
        mobile.put("area_code", digits.substring(0, 2));
        mobile.put("number", digits.substring(2));
        Map<String, Object> phones = new LinkedHashMap<>();
        phones.put("mobile_phone", mobile);
        return phones;
    }

    private GatewayPaymentResponse toPaymentResponse(JsonNode charge, JsonNode order) {
        JsonNode transaction = charge.path("last_transaction");
        BigDecimal amount = fromCentsOrNull(charge.path("amount"));
        String externalReference = firstNonBlank(
                text(charge.path("metadata"), "external_reference"),
                text(order.path("metadata"), "external_reference"),
                text(order, "code"));
        return new GatewayPaymentResponse(
                Gateway.PAGAR_ME,
                text(charge, "id"),
                text(charge, "status"),
                text(transaction, "status"),
                amount,
                orZero(fromCentsOrNull(charge.path("canceled_amount"))),
                parseDateTime(text(charge, "paid_at")),
                externalReference,
                transaction.hasNonNull("installments") ? transaction.path("installments").asInt() : null,
                text(transaction, "qr_code"),
                null,
                firstNonBlank(text(transaction, "qr_code_url"), text(transaction, "url"), text(transaction, "pdf")),
                text(transaction, "barcode"),
                text(transaction, "line"),
                parseDateTime(text(transaction, "expires_at")),
                charge);
    }

    private JsonNode send(
            MerchantCredentials credentials, HttpMethod method, String path, Object payload, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(path).headers(headers -> {
                headers.setBasicAuth(credentials.accessToken(), "");
                if (method != HttpMethod.GET) {
                    headers.set("Idempotency-Key", StringUtils.hasText(idempotencyKey)
                            ? idempotencyKey
                            : UUID.randomUUID().toString());
                }
            });
            if (payload != null) {
                request.body(payload);
            }
            JsonNode response = request.retrieve().body(JsonNode.class);
            if (response == null) {
                throw new GatewayIntegrationException("pagar.me returned empty response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            log.error("pagar.me request failed method={} path={} statusCode={} body={}",
                    method, path, exception.getStatusCode(), sanitizeBody(exception.getResponseBodyAsString()));
            throw new GatewayIntegrationException("pagar.me request failed: " + pagarMeMessage(exception), exception);
        } catch (ResourceAccessException exception) {
            log.error("pagar.me unreachable method={} path={} error={}", method, path, exception.getMessage());
            throw new GatewayIntegrationException("pagar.me unreachable", exception);
        }
    }

    private String pagarMeMessage(RestClientResponseException exception) {
        if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return "invalid secret key";
        }
        try {
            JsonNode body = exception.getResponseBodyAs(JsonNode.class);
            if (body == null) {
                return exception.getStatusCode().toString();
            }
            List<String> messages = new ArrayList<>();
            body.path("errors").fields().forEachRemaining(entry -> entry.getValue()
                    .forEach(message -> messages.add(entry.getKey() + ": " + message.asText())));
            String message = text(body, "message");
            if (!messages.isEmpty()) {
                return String.join("; ", messages);
            }
            return StringUtils.hasText(message) ? message : exception.getStatusCode().toString();
        } catch (RuntimeException ignored) {
            return exception.getStatusCode().toString();
        }
    }

    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static BigDecimal fromCentsOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : fromCents(node.asLong());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static java.time.LocalDateTime parseDateTime(String value) {
        // O Pagar.me devolve UTC; o billing guarda horário de Brasília (como o Mercado Pago, que já vem com -03:00).
        return StringUtils.hasText(value)
                ? OffsetDateTime.parse(value).atZoneSameInstant(BRASILIA).toLocalDateTime()
                : null;
    }

    private static String fullName(DirectPaymentRequest.Payer payer) {
        return joinNonBlank(" ", payer.firstName(), payer.lastName());
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(separator, parts);
    }

    private static String digitsOnly(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String sanitizeBody(String value) {
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }
        return value.length() > 1000 ? value.substring(0, 1000) + "...(truncated)" : value;
    }
}
