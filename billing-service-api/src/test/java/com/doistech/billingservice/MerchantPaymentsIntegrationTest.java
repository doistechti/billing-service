package com.doistech.billingservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.doistech.billingservice.callback.CallbackDispatcher;
import com.doistech.billingservice.callback.CallbackSigner;
import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.merchant.MerchantRepository;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.crypto.SecretCipher;
import com.doistech.billingservice.shared.crypto.Hmac;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo completo com Postgres real e um Mercado Pago falso: merchants, isolamento, Pix com idempotência,
 * webhook assinado, callback assinado, cartão, estorno e compatibilidade com o merchant legacy.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MerchantPaymentsIntegrationTest {

    private static final String ADMIN_KEY = "admin-key-test";
    private static final String LEGACY_KEY = "legacy-key-test";
    private static final String LEGACY_TOKEN = "LEGACY-MP-TOKEN";
    private static final String STORE_TOKEN = "STORE-MP-TOKEN";
    private static final String WEBHOOK_SECRET = "mp-webhook-secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final FakeMercadoPago mercadoPago = new FakeMercadoPago();
    static final CallbackReceiver callbackReceiver = new CallbackReceiver();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mercado-pago.base-url", mercadoPago::url);
        registry.add("mercado-pago.access-token", () -> LEGACY_TOKEN);
        registry.add("mercado-pago.webhook-secret", () -> "");
        registry.add("app.security.api-key", () -> LEGACY_KEY);
        registry.add("app.security.admin-api-key", () -> ADMIN_KEY);
        registry.add("app.security.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.callbacks.enabled", () -> "false");
    }

    @BeforeAll
    static void startFakes() {
        mercadoPago.start();
        callbackReceiver.start();
    }

    @AfterAll
    static void stopFakes() {
        mercadoPago.stop();
        callbackReceiver.stop();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CallbackDispatcher callbackDispatcher;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private SecretCipher cipher;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void resetFakes() {
        // HttpURLConnection não lê respostas 401 de POST; o cliente java.net.http lê.
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        mercadoPago.reset();
        // Os cenários compartilham o banco: esvazia os callbacks deixados pelos anteriores.
        callbackDispatcher.dispatchDue();
        callbackReceiver.requests.clear();
    }

    @Test
    void pixChargeIsIdempotentIsolatedConfirmedBySignedWebhookAndRefundable() throws Exception {
        assertThat(post("/api/admin/merchants", Map.of("code", "x", "name", "x"), Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        JsonNode store = createMerchant("store-aurea", STORE_TOKEN, WEBHOOK_SECRET);
        String apiKey = store.path("apiKey").asText();
        String callbackSecret = store.path("callbackSecret").asText();
        String merchantId = store.path("merchant").path("id").asText();
        assertThat(store.path("merchant").path("mercadoPagoNotificationUrl").asText())
                .endsWith("/api/webhooks/mercado-pago/" + merchantId);

        String customerId = createCustomer(apiKey, "CLIENTE-1");
        assertThat(post("/api/customers", customerBody("CLIENTE-1"), auth(apiKey)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        String invoiceId = createInvoice(apiKey, customerId, "PED-10491");

        Map<String, Object> pixRequest = Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "PIX");
        Map<String, String> idempotent = Map.of("X-API-Key", apiKey, "Idempotency-Key", "order-10491-attempt-1");
        ResponseEntity<JsonNode> created = post("/api/charges", pixRequest, idempotent);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode charge = created.getBody();
        assertThat(charge.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        assertThat(charge.path("method").asText()).isEqualTo("PIX");
        assertThat(charge.path("pix").path("qrCode").asText()).startsWith("00020126");
        assertThat(charge.path("expiresAt").isNull()).isFalse();

        JsonNode replay = post("/api/charges", pixRequest, idempotent).getBody();
        assertThat(replay.path("id").asText()).isEqualTo(charge.path("id").asText());
        assertThat(mercadoPago.createdPayments).hasSize(1);
        JsonNode sentToGateway = mercadoPago.createdPayments.get(0);
        assertThat(sentToGateway.path("_idempotencyKey").asText()).isEqualTo("order-10491-attempt-1");
        assertThat(sentToGateway.path("_authorization").asText()).isEqualTo("Bearer " + STORE_TOKEN);
        assertThat(sentToGateway.path("payer").path("identification").path("number").asText()).isEqualTo("12345678909");

        JsonNode other = createMerchant("store-patinha", "OTHER-TOKEN", null);
        String otherKey = other.path("apiKey").asText();
        assertThat(get("/api/invoices/" + invoiceId, otherKey).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/charges/" + charge.path("id").asText(), otherKey).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String paymentId = charge.path("gatewayPaymentId").asText();
        mercadoPago.setStatus(paymentId, "approved", "accredited");
        String webhookPath = "/api/webhooks/mercado-pago/" + merchantId + "?data.id=" + paymentId + "&type=payment";
        Map<String, Object> webhookBody = Map.of("action", "payment.updated", "type", "payment", "data", Map.of("id", paymentId));
        assertThat(post(webhookPath, webhookBody, Map.of("x-signature", "ts=1,v1=bad", "x-request-id", "r1")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String v1 = Hmac.sha256Hex(WEBHOOK_SECRET, "id:" + paymentId + ";request-id:r1;ts:1704908010;");
        assertThat(post(webhookPath, webhookBody, Map.of("x-signature", "ts=1704908010,v1=" + v1, "x-request-id", "r1"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode paid = get("/api/charges/" + charge.path("id").asText(), apiKey).getBody();
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(get("/api/invoices/" + invoiceId, apiKey).getBody().path("status").asText()).isEqualTo("PAID");

        assertThat(callbackDispatcher.dispatchDue()).isEqualTo(1);
        assertThat(callbackReceiver.requests).hasSize(1);
        ReceivedCallback callback = callbackReceiver.requests.get(0);
        assertThat(callback.headers.get("X-billing-event")).isEqualTo("charge.paid");
        long timestamp = Long.parseLong(callback.headers.get("X-billing-timestamp"));
        assertThat(callback.headers.get("X-billing-signature")).isEqualTo(CallbackSigner.sign(callbackSecret, timestamp, callback.body));
        JsonNode callbackBody = json.readTree(callback.body);
        assertThat(callbackBody.path("data").path("externalReference").asText()).isEqualTo("PED-10491");
        assertThat(callbackBody.path("data").path("invoiceStatus").asText()).isEqualTo("PAID");

        JsonNode partial = post("/api/charges/" + charge.path("id").asText() + "/refund", Map.of("amount", 50), auth(apiKey)).getBody();
        assertThat(partial.path("status").asText()).isEqualTo("PAID");
        assertThat(partial.path("refundedAmount").decimalValue()).isEqualByComparingTo("50");
        JsonNode full = post("/api/charges/" + charge.path("id").asText() + "/refund", Map.of(), auth(apiKey)).getBody();
        assertThat(full.path("status").asText()).isEqualTo("REFUNDED");
        assertThat(get("/api/invoices/" + invoiceId, apiKey).getBody().path("status").asText()).isEqualTo("REFUNDED");
        assertThat(mercadoPago.refunds).hasSize(2);
        assertThat(mercadoPago.refunds.get(1).has("amount")).isFalse();
    }

    @Test
    void rejectedCardReopensInvoiceAndApprovedCardPaysSynchronously() {
        String apiKey = createMerchant("store-circuito", STORE_TOKEN, null).path("apiKey").asText();
        String invoiceId = createInvoice(apiKey, createCustomer(apiKey, "CLIENTE-9"), "PED-9");

        JsonNode rejected = post("/api/charges", cardRequest(invoiceId, "rejected-token"), auth(apiKey)).getBody();
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("statusDetail").asText()).isEqualTo("cc_rejected_insufficient_amount");
        assertThat(get("/api/invoices/" + invoiceId, apiKey).getBody().path("status").asText()).isEqualTo("OPEN");

        JsonNode approved = post("/api/charges", cardRequest(invoiceId, "approved-token"), auth(apiKey)).getBody();
        assertThat(approved.path("status").asText()).isEqualTo("PAID");
        assertThat(approved.path("installments").asInt()).isEqualTo(10);
        assertThat(get("/api/invoices/" + invoiceId, apiKey).getBody().path("status").asText()).isEqualTo("PAID");
        assertThat(get("/api/payments/invoice/" + invoiceId, apiKey).getBody()).hasSize(2);
    }

    @Test
    void boletoRequiresAddressAndReturnsDigitableLine() {
        String apiKey = createMerchant("store-boleto", STORE_TOKEN, null).path("apiKey").asText();
        String invoiceId = createInvoice(apiKey, createCustomer(apiKey, "CLIENTE-B"), "PED-B");

        Map<String, Object> withoutAddress = Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "BOLETO");
        assertThat(post("/api/charges", withoutAddress, auth(apiKey)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> address = Map.of("zipCode", "01310100", "street", "Av. Paulista", "number", "1000",
                "neighborhood", "Bela Vista", "city", "São Paulo", "state", "SP");
        Map<String, Object> request = Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "BOLETO",
                "payer", Map.of("address", address));
        JsonNode boleto = post("/api/charges", request, auth(apiKey)).getBody();
        assertThat(boleto.path("boleto").path("digitableLine").asText()).isEqualTo("23790.00009 12345.678901 23456.789012 1 99990000036970");
        assertThat(boleto.path("boleto").path("pdfUrl").asText()).startsWith("https://");

        JsonNode canceled = post("/api/charges/" + boleto.path("id").asText() + "/cancel", Map.of(), auth(apiKey)).getBody();
        assertThat(canceled.path("status").asText()).isEqualTo("CANCELED");
        assertThat(get("/api/invoices/" + invoiceId, apiKey).getBody().path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void legacyApiKeyKeepsCheckoutProWorking() {
        assertThat(get("/api/merchant", "wrong-key").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/merchant", LEGACY_KEY).getBody().path("code").asText()).isEqualTo("legacy");

        String invoiceId = createInvoice(LEGACY_KEY, createCustomer(LEGACY_KEY, "ALUNO-123"), "ALUNO-123-MENSALIDADE-2026-07");
        JsonNode charge = post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO"), auth(LEGACY_KEY)).getBody();

        assertThat(charge.path("method").asText()).isEqualTo("CHECKOUT_PRO");
        assertThat(charge.path("paymentUrl").asText()).isEqualTo("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1");
        assertThat(mercadoPago.lastAuthorization).isEqualTo("Bearer " + LEGACY_TOKEN);
    }

    /**
     * V4 (SDD 10): merchants existentes seguem MERCADO_PAGO; um merchant PAGAR_ME resolve as credenciais dele
     * (secret key cifrada, rota de webhook própria) e ainda não aceita cobrança enquanto o gateway não existe.
     */
    @Test
    void merchantGatewayDefaultsToMercadoPagoAndPagarMeMerchantResolvesItsOwnCredentials() {
        createMerchant("store-default-gateway", STORE_TOKEN, null);
        assertThat(merchantRepository.findAll()).isNotEmpty()
                .allSatisfy(merchant -> assertThat(merchant.getGateway()).isEqualTo(Gateway.MERCADO_PAGO));

        Merchant curso = new Merchant();
        curso.setCode("curso-pagarme");
        curso.setName("Curso livre");
        curso.setGateway(Gateway.PAGAR_ME);
        curso.setPagarMeSecretKeyEncrypted(cipher.encrypt("sk_test_curso"));
        curso.setPagarMePublicKey("pk_test_curso");
        curso.setPagarMeWebhookSecretEncrypted(cipher.encrypt("whsec-curso"));
        curso.setActive(true);
        Merchant saved = merchantRepository.save(curso);

        MerchantCredentials credentials = merchantService.credentials(saved.getId());
        assertThat(credentials.accessToken()).isEqualTo("sk_test_curso");
        assertThat(credentials.publicKey()).isEqualTo("pk_test_curso");
        assertThat(credentials.webhookSecret()).isEqualTo("whsec-curso");
        assertThat(credentials.notificationUrl()).endsWith("/api/webhooks/pagar-me/" + saved.getId());
        assertThat(merchantService.webhookSecret(saved.getId())).contains("whsec-curso");
        assertThat(merchantService.gatewayOf(saved.getId())).isEqualTo(Gateway.PAGAR_ME);
    }

    /**
     * Contrato consumido pela doistech-store (BilinsBillingGateway). Só confere que os campos que a store lê
     * continuam presentes com o mesmo tipo: campos novos são permitidos, remover ou renomear estes não é.
     * Ver SDD 10 — regra de compatibilidade.
     */
    @Test
    void storeConsumedJsonContractStaysStable() {
        JsonNode created = createMerchant("store-contrato", STORE_TOKEN, null);
        assertHasFields(created, "apiKey", "callbackSecret", "merchant");
        assertHasFields(created.path("merchant"), "id", "code", "mercadoPagoNotificationUrl");
        String apiKey = created.path("apiKey").asText();

        JsonNode merchant = get("/api/merchant", apiKey).getBody();
        assertHasFields(merchant, "id", "code", "mercadoPagoPublicKey", "mercadoPagoConfigured", "sandbox",
                "webhookSignatureEnabled", "mercadoPagoNotificationUrl");
        assertThat(merchant.path("mercadoPagoConfigured").isBoolean()).isTrue();
        assertThat(merchant.path("sandbox").isBoolean()).isTrue();
        assertThat(merchant.path("webhookSignatureEnabled").isBoolean()).isTrue();

        ResponseEntity<JsonNode> customerResponse = post("/api/customers", customerBody("CLIENTE-C"), auth(apiKey));
        assertThat(customerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertHasFields(customerResponse.getBody(), "id");
        String customerId = customerResponse.getBody().path("id").asText();
        assertThat(get("/api/customers/by-reference?sourceSystem=STORE&externalReference=CLIENTE-C", apiKey)
                .getBody().path("id").asText()).isEqualTo(customerId);

        String invoiceId = createInvoice(apiKey, customerId, "PED-C");
        JsonNode invoice = get("/api/invoices/by-reference?sourceSystem=STORE&externalReference=PED-C", apiKey).getBody();
        assertHasFields(invoice, "id", "totalAmount", "status");
        assertThat(invoice.path("id").asText()).isEqualTo(invoiceId);
        assertThat(invoice.path("totalAmount").isNumber()).isTrue();

        JsonNode pix = post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "PIX"),
                Map.of("X-API-Key", apiKey, "Idempotency-Key", "contrato-pix")).getBody();
        assertHasFields(pix, "id", "invoiceId", "status", "amount", "method", "refundedAmount", "expiresAt");
        assertHasFields(pix.path("pix"), "qrCode", "qrCodeBase64", "ticketUrl");
        assertThat(pix.path("amount").isNumber()).isTrue();
        String pixId = pix.path("id").asText();

        JsonNode synced = post("/api/charges/" + pixId + "/sync", Map.of(), auth(apiKey)).getBody();
        assertHasFields(synced, "id", "invoiceId", "status", "amount", "method");
        assertThat(get("/api/charges/" + pixId, apiKey).getBody().path("id").asText()).isEqualTo(pixId);
        JsonNode canceled = post("/api/charges/" + pixId + "/cancel", Map.of(), auth(apiKey)).getBody();
        assertThat(canceled.path("status").asText()).isEqualTo("CANCELED");

        JsonNode card = post("/api/charges", cardRequest(invoiceId, "approved-token"), auth(apiKey)).getBody();
        assertHasFields(card, "id", "status", "statusDetail", "installments", "paidAt");
        assertThat(card.path("status").asText()).isEqualTo("PAID");
        JsonNode refunded = post("/api/charges/" + card.path("id").asText() + "/refund", Map.of(), auth(apiKey)).getBody();
        assertHasFields(refunded, "id", "status", "refundedAmount");

        String boletoInvoiceId = createInvoice(apiKey, customerId, "PED-C2");
        Map<String, Object> address = Map.of("zipCode", "01310100", "street", "Av. Paulista", "number", "1000",
                "neighborhood", "Bela Vista", "city", "São Paulo", "state", "SP");
        JsonNode boleto = post("/api/charges", Map.of("invoiceId", boletoInvoiceId, "gateway", "MERCADO_PAGO",
                "method", "BOLETO", "payer", Map.of("address", address)), auth(apiKey)).getBody();
        assertHasFields(boleto.path("boleto"), "barcode", "digitableLine", "pdfUrl");
    }

    private static void assertHasFields(JsonNode node, String... fields) {
        for (String field : fields) {
            assertThat(node.has(field)).as("campo '%s' em %s", field, node).isTrue();
        }
    }

    @Test
    void merchantConfiguresOwnMercadoPagoCredentialsAfterVerification() {
        ResponseEntity<JsonNode> created = post("/api/admin/merchants",
                Map.of("code", "store-selfservice", "name", "Loja Autoatendimento", "callbackUrl", callbackReceiver.url()),
                Map.of("X-Admin-Key", ADMIN_KEY));
        String apiKey = created.getBody().path("apiKey").asText();
        JsonNode before = get("/api/merchant", apiKey).getBody();
        assertThat(before.path("mercadoPagoConfigured").asBoolean()).isFalse();
        assertThat(before.path("mercadoPagoNotificationUrl").asText()).contains("/api/webhooks/mercado-pago/");

        String invoiceId = createInvoice(apiKey, createCustomer(apiKey, "CLIENTE-SS"), "PED-SS");
        assertThat(post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "PIX"), auth(apiKey))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        java.util.function.Function<Map<String, Object>, ResponseEntity<JsonNode>> put = body ->
                rest.exchange("/api/merchant/mercado-pago", HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(put.apply(Map.of("publicKey", "TEST-public")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put.apply(Map.of("accessToken", "TEST-token", "publicKey", "APP_USR-public")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<JsonNode> invalid = put.apply(Map.of("accessToken", "TEST-INVALID", "publicKey", "TEST-public"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().path("message").asText()).contains("rejected");

        JsonNode saved = put.apply(Map.of("accessToken", "TEST-SELF-TOKEN", "publicKey", "TEST-self-public",
                "webhookSecret", "self-secret")).getBody();
        assertThat(saved.path("mercadoPagoConfigured").asBoolean()).isTrue();
        assertThat(saved.path("sandbox").asBoolean()).isTrue();
        assertThat(saved.path("webhookSignatureEnabled").asBoolean()).isTrue();
        assertThat(saved.path("mercadoPagoAccountNickname").asText()).isEqualTo("LOJA_TESTE");
        assertThat(saved.toString()).doesNotContain("TEST-SELF-TOKEN").doesNotContain("self-secret");

        JsonNode keepToken = put.apply(Map.of("publicKey", "TEST-self-public-2")).getBody();
        assertThat(keepToken.path("mercadoPagoPublicKey").asText()).isEqualTo("TEST-self-public-2");

        post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "PIX"), auth(apiKey));
        assertThat(mercadoPago.createdPayments.get(0).path("_authorization").asText()).isEqualTo("Bearer TEST-SELF-TOKEN");

        HttpHeaders legacy = new HttpHeaders();
        legacy.set("X-API-Key", LEGACY_KEY);
        legacy.setContentType(MediaType.APPLICATION_JSON);
        assertThat(rest.exchange("/api/merchant/mercado-pago", HttpMethod.PUT,
                new HttpEntity<>(Map.of("accessToken", "TEST-x", "publicKey", "TEST-y"), legacy), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> cardRequest(String invoiceId, String token) {
        return Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "CREDIT_CARD",
                "card", Map.of("token", token, "paymentMethodId", "visa", "installments", 10));
    }

    private JsonNode createMerchant(String code, String accessToken, String webhookSecret) {
        ObjectNode body = json.createObjectNode()
                .put("code", code)
                .put("name", code)
                .put("mercadoPagoAccessToken", accessToken)
                .put("mercadoPagoPublicKey", "APP_USR-public")
                .put("callbackUrl", callbackReceiver.url());
        if (webhookSecret != null) {
            body.put("mercadoPagoWebhookSecret", webhookSecret);
        }
        ResponseEntity<JsonNode> response = post("/api/admin/merchants", body, Map.of("X-Admin-Key", ADMIN_KEY));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> customerBody(String reference) {
        return Map.of("sourceSystem", "STORE", "externalReference", reference, "name", "Pedro Almeida",
                "email", "pedro@example.com", "document", "123.456.789-09");
    }

    private String createCustomer(String apiKey, String reference) {
        ResponseEntity<JsonNode> response = post("/api/customers", customerBody(reference), auth(apiKey));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("id").asText();
    }

    private String createInvoice(String apiKey, String customerId, String reference) {
        Map<String, Object> body = Map.of(
                "customerId", customerId,
                "sourceSystem", "STORE",
                "externalReference", reference,
                "description", "Pedido " + reference,
                "items", List.of(
                        Map.of("description", "Mochila urbana 20 L", "quantity", 1, "unitAmount", 289.90),
                        Map.of("description", "Caneca de cerâmica", "quantity", 1, "unitAmount", 59.90)));
        ResponseEntity<JsonNode> response = post("/api/invoices", body, auth(apiKey));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("id").asText();
    }

    private Map<String, String> auth(String apiKey) {
        return Map.of("X-API-Key", apiKey);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String apiKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-API-Key", apiKey);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(httpHeaders), JsonNode.class);
    }

    /**
     * Imita os endpoints do Mercado Pago usados pelo serviço.
     */
    static class FakeMercadoPago {

        private final ObjectMapper json = new ObjectMapper();
        private final AtomicLong ids = new AtomicLong(1000);
        final Map<String, ObjectNode> payments = new ConcurrentHashMap<>();
        final List<JsonNode> createdPayments = new CopyOnWriteArrayList<>();
        final List<JsonNode> refunds = new CopyOnWriteArrayList<>();
        final Map<String, String> paymentsByIdempotencyKey = new ConcurrentHashMap<>();
        volatile String lastAuthorization;
        private HttpServer server;

        void start() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/", this::handle);
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            payments.clear();
            createdPayments.clear();
            refunds.clear();
            paymentsByIdempotencyKey.clear();
        }

        void setStatus(String paymentId, String status, String detail) {
            ObjectNode payment = payments.get(paymentId);
            payment.put("status", status).put("status_detail", detail);
            if ("approved".equals(status)) {
                payment.put("date_approved", "2026-09-25T21:00:00.000-03:00");
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            JsonNode body = requestBytes.length == 0 ? json.createObjectNode() : json.readTree(requestBytes);

            ObjectNode response;
            if ("GET".equals(method) && path.equals("/users/me")) {
                boolean valid = lastAuthorization != null && !lastAuthorization.contains("INVALID");
                byte[] me = valid
                        ? json.writeValueAsBytes(json.createObjectNode().put("id", 777001).put("nickname", "LOJA_TESTE").put("site_id", "MLB"))
                        : "{\"message\":\"invalid access token\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? 200 : 401, me.length);
                exchange.getResponseBody().write(me);
                exchange.close();
                return;
            }
            if ("POST".equals(method) && path.equals("/checkout/preferences")) {
                response = json.createObjectNode()
                        .put("id", "pref-1")
                        .put("init_point", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1")
                        .put("sandbox_init_point", "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1");
            } else if ("POST".equals(method) && path.equals("/v1/payments")) {
                response = createPayment(exchange, (ObjectNode) body);
            } else if ("POST".equals(method) && path.matches("/v1/payments/\\d+/refunds")) {
                refunds.add(body);
                String paymentId = path.split("/")[3];
                double amount = body.has("amount")
                        ? body.path("amount").asDouble()
                        : payments.get(paymentId).path("transaction_amount").asDouble();
                response = json.createObjectNode().put("id", ids.incrementAndGet()).put("status", "approved").put("amount", amount);
            } else if ("PUT".equals(method) && path.matches("/v1/payments/\\d+")) {
                String paymentId = path.substring("/v1/payments/".length());
                setStatus(paymentId, body.path("status").asText(), "by_collector");
                response = payments.get(paymentId);
            } else if ("GET".equals(method) && path.matches("/v1/payments/\\d+")) {
                response = payments.get(path.substring("/v1/payments/".length()));
            } else {
                response = null;
            }

            byte[] bytes = response == null ? "{\"message\":\"not found\"}".getBytes(StandardCharsets.UTF_8)
                    : json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response == null ? 404 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private ObjectNode createPayment(HttpExchange exchange, ObjectNode body) {
            String idempotencyKey = exchange.getRequestHeaders().getFirst("X-Idempotency-Key");
            String existing = paymentsByIdempotencyKey.get(idempotencyKey);
            if (existing != null) {
                return payments.get(existing);
            }
            ObjectNode recorded = body.deepCopy();
            recorded.put("_idempotencyKey", idempotencyKey);
            recorded.put("_authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            createdPayments.add(recorded);

            String id = String.valueOf(ids.incrementAndGet());
            ObjectNode payment = json.createObjectNode()
                    .put("id", Long.parseLong(id))
                    .put("transaction_amount", body.path("transaction_amount").decimalValue())
                    .put("external_reference", body.path("external_reference").asText());
            String methodId = body.path("payment_method_id").asText();
            if ("pix".equals(methodId)) {
                payment.put("status", "pending").put("status_detail", "pending_waiting_transfer")
                        .put("date_of_expiration", body.path("date_of_expiration").asText());
                payment.putObject("point_of_interaction").putObject("transaction_data")
                        .put("qr_code", "00020126580014br.gov.bcb.pix0136fake")
                        .put("qr_code_base64", "iVBORw0KGgo=")
                        .put("ticket_url", "https://www.mercadopago.com.br/payments/" + id + "/ticket");
            } else if ("bolbradesco".equals(methodId)) {
                payment.put("status", "pending").put("status_detail", "pending_waiting_payment");
                payment.putObject("barcode").put("content", "23791999900000369700000912345678901234567890");
                payment.putObject("transaction_details")
                        .put("external_resource_url", "https://www.mercadopago.com.br/payments/" + id + "/ticket?caller_id=1")
                        .put("digitable_line", "23790.00009 12345.678901 23456.789012 1 99990000036970");
            } else if ("approved-token".equals(body.path("token").asText())) {
                payment.put("status", "approved").put("status_detail", "accredited")
                        .put("installments", body.path("installments").asInt())
                        .put("date_approved", "2026-09-25T21:00:00.000-03:00");
            } else {
                payment.put("status", "rejected").put("status_detail", "cc_rejected_insufficient_amount")
                        .put("installments", body.path("installments").asInt());
            }
            payments.put(id, payment);
            paymentsByIdempotencyKey.put(idempotencyKey, id);
            return payment;
        }
    }

    record ReceivedCallback(Map<String, String> headers, String body) {
    }

    /**
     * Sistema cliente falso que recebe os callbacks.
     */
    static class CallbackReceiver {

        final List<ReceivedCallback> requests = new CopyOnWriteArrayList<>();
        private HttpServer server;

        void start() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/billing/callback", exchange -> {
                Map<String, String> headers = new ConcurrentHashMap<>();
                exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, values.get(0)));
                requests.add(new ReceivedCallback(headers,
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/billing/callback";
        }
    }
}
