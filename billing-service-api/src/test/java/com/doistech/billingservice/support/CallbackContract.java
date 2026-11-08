package com.doistech.billingservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Contrato do callback que a doistech-store consome (BillingWebhookController, API-CONTRACT.md da store).
 *
 * <p>A verificação é uma reimplementação independente do que a store faz, sem usar o {@code CallbackSigner} do billing:
 * se o signer mudar de algoritmo ou formato, este teste acusa em vez de acompanhar a mudança.
 *
 * <ul>
 *   <li>assinatura: {@code X-Billing-Signature = v1=hex(HMAC-SHA256(callbackSecret, timestamp + "." + body))};
 *   <li>{@code X-Billing-Timestamp} em segundos, aceito pela store dentro de 5 minutos;
 *   <li>o {@code id} do corpo (UUID) é a chave de idempotência e é o mesmo de {@code X-Billing-Delivery};
 *   <li>o evento e o status da charge andam juntos (charge.paid → PAID etc.).
 * </ul>
 */
public final class CallbackContract {

    private static final ObjectMapper JSON = new ObjectMapper();

    private CallbackContract() {
    }

    /** Confere o callback como a store o faria e devolve o corpo já lido. */
    public static JsonNode assertStoreCompatible(
            String callbackSecret, Map<String, String> headers, String body, String expectedEvent, String expectedStatus) {
        String event = header(headers, "X-Billing-Event");
        String delivery = header(headers, "X-Billing-Delivery");
        String timestamp = header(headers, "X-Billing-Timestamp");
        String signature = header(headers, "X-Billing-Signature");

        assertThat(event).isEqualTo(expectedEvent);
        assertThat(signature).startsWith("v1=");
        assertThat(signature).isEqualTo("v1=" + hmacSha256Hex(callbackSecret, timestamp + "." + body));
        assertThat(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp))).isLessThan(300);

        JsonNode root = read(body);
        assertThat(UUID.fromString(root.path("id").asText())).hasToString(delivery);
        assertThat(root.path("event").asText()).isEqualTo(expectedEvent);
        assertThat(Instant.parse(root.path("occurredAt").asText())).isNotNull();

        JsonNode data = root.path("data");
        assertThat(UUID.fromString(data.path("chargeId").asText())).isNotNull();
        assertThat(UUID.fromString(data.path("invoiceId").asText())).isNotNull();
        for (String field : new String[] {"sourceSystem", "externalReference", "method", "status", "invoiceStatus"}) {
            assertThat(data.path(field).isTextual()).as("data.%s deve ser texto em %s", field, data).isTrue();
        }
        assertThat(data.path("status").asText()).isEqualTo(expectedStatus);
        assertThat(data.path("amount").isNumber()).as("data.amount numérico em %s", data).isTrue();
        assertThat(data.path("refundedAmount").isNumber()).as("data.refundedAmount numérico em %s", data).isTrue();
        // A store lê estes três quando existem; a chave precisa continuar presente no envelope.
        for (String field : new String[] {"statusDetail", "gatewayPaymentId", "paidAt"}) {
            assertThat(data.has(field)).as("data.%s presente em %s", field, data).isTrue();
        }
        if ("PAID".equals(expectedStatus)) {
            // Data sem fuso (horário de Brasília): a store lê como LocalDateTime.
            assertThat(LocalDateTime.parse(data.path("paidAt").asText())).isNotNull();
            assertThat(data.path("gatewayPaymentId").asText()).isNotBlank();
        }
        return root;
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("cabeçalho ausente no callback: " + name));
    }

    private static JsonNode read(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception exception) {
            throw new AssertionError("corpo do callback não é JSON: " + body, exception);
        }
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
