package com.doistech.billingservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.core.merchant.MerchantRepository;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.crypto.Hmac;
import com.doistech.billingservice.shared.crypto.SecretCipher;
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
 * Fluxo completo de um merchant Pagar.me (um curso livre) com Postgres real e um Pagar.me falso: Pix, autenticação
 * do webhook, os dois formatos de evento (charge.* e order.*), idempotência, estorno e rotas de outro gateway.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PagarMePaymentsIntegrationTest {

    private static final String SECRET_KEY = "sk_test_curso";
    private static final String WEBHOOK_SECRET = "whsec-curso";
    private static final String API_KEY = "bk_curso_pagarme_test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final FakePagarMe pagarMe = new FakePagarMe();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pagar-me.base-url", () -> pagarMe.url() + "/core/v5");
        registry.add("mercado-pago.access-token", () -> "UNUSED");
        registry.add("mercado-pago.webhook-secret", () -> "");
        registry.add("app.security.api-key", () -> "legacy-key-test");
        registry.add("app.security.admin-api-key", () -> "admin-key-test");
        registry.add("app.security.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.callbacks.enabled", () -> "false");
    }

    @BeforeAll
    static void startFake() {
        pagarMe.start();
    }

    @AfterAll
    static void stopFake() {
        pagarMe.stop();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private SecretCipher cipher;

    private final ObjectMapper json = new ObjectMapper();
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        pagarMe.requests.clear();
        merchant = merchantRepository.findAll().stream()
                .filter(candidate -> "curso-livre".equals(candidate.getCode())).findFirst()
                .orElseGet(this::createPagarMeMerchant);
    }

    private Merchant createPagarMeMerchant() {
        Merchant curso = new Merchant();
        curso.setCode("curso-livre");
        curso.setName("Curso livre");
        curso.setGateway(Gateway.PAGAR_ME);
        curso.setApiKeyHash(Hmac.sha256Hex(API_KEY));
        curso.setApiKeyPrefix(API_KEY.substring(0, 8));
        curso.setPagarMeSecretKeyEncrypted(cipher.encrypt(SECRET_KEY));
        curso.setPagarMePublicKey("pk_test_curso");
        curso.setPagarMeWebhookSecretEncrypted(cipher.encrypt(WEBHOOK_SECRET));
        curso.setActive(true);
        return merchantRepository.save(curso);
    }

    @Test
    void pixChargeIsConfirmedByAuthenticatedWebhookAndIsIdempotent() {
        String invoiceId = createInvoice("INSC-1");

        JsonNode charge = post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "PAGAR_ME", "method", "PIX"),
                Map.of("X-API-Key", API_KEY)).getBody();
        assertThat(charge.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        assertThat(charge.path("method").asText()).isEqualTo("PIX");
        assertThat(charge.path("pix").path("qrCode").asText()).startsWith("00020126");
        String chargeId = charge.path("id").asText();
        String gatewayChargeId = charge.path("gatewayPaymentId").asText();
        assertThat(pagarMe.requests.get(0).authorization())
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)));

        String path = "/api/webhooks/pagar-me/" + merchant.getId();
        Map<String, Object> event = chargeEvent("charge.paid", gatewayChargeId);

        // Sem credencial, credencial errada e credencial de outro formato: 401 e a charge não muda.
        assertThat(post(path, event, Map.of()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(path + "?token=errado", event, Map.of()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(path, event, Map.of("Authorization", basic("hook", "errado"))).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/charges/" + chargeId).getBody().path("status").asText()).isEqualTo("WAITING_PAYMENT");

        // Credencial correta, mas o Pagar.me ainda diz "pending": o payload sozinho não confirma nada.
        assertThat(post(path, event, Map.of("Authorization", basic("hook", WEBHOOK_SECRET))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/charges/" + chargeId).getBody().path("status").asText()).isEqualTo("WAITING_PAYMENT");

        // O Pagar.me passa a dizer "paid": o webhook confirma pela consulta.
        pagarMe.markPaid(gatewayChargeId);
        assertThat(post(path, event, Map.of("Authorization", basic("hook", WEBHOOK_SECRET))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode paid = get("/api/charges/" + chargeId).getBody();
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("paidAt").isNull()).isFalse();
        assertThat(get("/api/invoices/" + invoiceId).getBody().path("status").asText()).isEqualTo("PAID");
        JsonNode payments = get("/api/payments/invoice/" + invoiceId).getBody();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).path("gateway").asText()).isEqualTo("PAGAR_ME");

        // Reenvio do mesmo evento: continua 200 e não duplica o pagamento.
        assertThat(post(path, event, Map.of("Authorization", basic("hook", WEBHOOK_SECRET))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/payments/invoice/" + invoiceId).getBody()).hasSize(1);

        // Estorno total pelo billing chega ao Pagar.me por DELETE.
        JsonNode refunded = post("/api/charges/" + chargeId + "/refund", Map.of(), Map.of("X-API-Key", API_KEY)).getBody();
        assertThat(refunded.path("status").asText()).isEqualTo("REFUNDED");
        assertThat(pagarMe.requests.stream().anyMatch(r -> r.method().equals("DELETE")
                && r.path().equals("/core/v5/charges/" + gatewayChargeId))).isTrue();
    }

    @Test
    void orderEventWithTokenParameterConfirmsTheFirstCharge() {
        String invoiceId = createInvoice("INSC-2");
        JsonNode charge = post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "PAGAR_ME", "method", "PIX"),
                Map.of("X-API-Key", API_KEY)).getBody();
        String gatewayChargeId = charge.path("gatewayPaymentId").asText();
        pagarMe.markPaid(gatewayChargeId);

        Map<String, Object> orderEvent = Map.of("id", "hook_2", "type", "order.paid",
                "data", Map.of("id", "or_x", "charges", List.of(Map.of("id", gatewayChargeId))));
        ResponseEntity<JsonNode> response = post(
                "/api/webhooks/pagar-me/" + merchant.getId() + "?token=" + WEBHOOK_SECRET, orderEvent, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/invoices/" + invoiceId).getBody().path("status").asText()).isEqualTo("PAID");
    }

    @Test
    void unrelatedEventsAreIgnoredWithoutCallingTheGateway() {
        Map<String, Object> event = Map.of("id", "hook_3", "type", "customer.created", "data", Map.of("id", "cus_1"));

        ResponseEntity<JsonNode> response = post(
                "/api/webhooks/pagar-me/" + merchant.getId() + "?token=" + WEBHOOK_SECRET, event, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagarMe.requests).isEmpty();
    }

    @Test
    void pagarMeMerchantRejectsOtherGatewaysAndHostedCheckoutAndOtherRoutes() {
        String invoiceId = createInvoice("INSC-3");

        ResponseEntity<JsonNode> mismatch = post("/api/charges",
                Map.of("invoiceId", invoiceId, "gateway", "MERCADO_PAGO", "method", "PIX"), Map.of("X-API-Key", API_KEY));
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mismatch.getBody().path("message").asText()).startsWith("gateway mismatch");

        ResponseEntity<JsonNode> hosted = post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "PAGAR_ME"),
                Map.of("X-API-Key", API_KEY));
        assertThat(hosted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hosted.getBody().path("message").asText()).contains("PIX, BOLETO or CREDIT_CARD");

        // A rota do Mercado Pago não atende merchant Pagar.me, e vice-versa.
        assertThat(post("/api/webhooks/mercado-pago/" + merchant.getId(), Map.of("type", "payment"), Map.of())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/webhooks/pagar-me/" + com.doistech.billingservice.core.merchant.Merchant.LEGACY_ID,
                Map.of("type", "charge.paid"), Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCreatesPagarMeMerchantAndItRegistersItsOwnCredentialsThenCharges() {
        ResponseEntity<JsonNode> mixed = post("/api/admin/merchants", Map.of("code", "curso-misto", "name", "Misto",
                "gateway", "PAGAR_ME", "mercadoPagoAccessToken", "TEST-x"), Map.of("X-Admin-Key", "admin-key-test"));
        assertThat(mixed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> created = post("/api/admin/merchants", Map.of("code", "curso-bible", "name", "Curso de Bíblia",
                "gateway", "PAGAR_ME"), Map.of("X-Admin-Key", "admin-key-test"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String key = created.getBody().path("apiKey").asText();
        JsonNode data = created.getBody().path("merchant");
        assertThat(data.path("gateway").asText()).isEqualTo("PAGAR_ME");
        assertThat(data.path("pagarMeConfigured").asBoolean(true)).isFalse();
        assertThat(data.path("mercadoPagoNotificationUrl").isNull()).isTrue();
        assertThat(data.path("notificationUrl").asText()).endsWith("/api/webhooks/pagar-me/" + data.path("id").asText());

        Map<String, String> auth = Map.of("X-API-Key", key);
        // Sem credenciais ainda: cobrar não é possível.
        String invoiceId = createInvoiceFor(key, "INSC-S1");
        assertThat(post("/api/charges", Map.of("invoiceId", invoiceId, "gateway", "PAGAR_ME", "method", "PIX"), auth)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Validações locais antes de chamar o Pagar.me.
        assertThat(put("/api/merchant/pagar-me", Map.of("publicKey", "pk_test_x"), auth).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST); // sem secret key no primeiro cadastro
        assertThat(put("/api/merchant/pagar-me", Map.of("secretKey", "abc", "publicKey", "pk_test_x"), auth).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST); // prefixo
        assertThat(put("/api/merchant/pagar-me", Map.of("secretKey", "sk_test_ok", "publicKey", "pk_live"), auth)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // ambientes misturados
        assertThat(put("/api/merchant/pagar-me", Map.of("secretKey", "sk_test_ok"), auth).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST); // sem public key
        int callsBeforeVerification = pagarMe.requests.size();

        // Chave recusada pelo Pagar.me.
        ResponseEntity<JsonNode> rejected = put("/api/merchant/pagar-me",
                Map.of("secretKey", "sk_test_bad", "publicKey", "pk_test_x"), auth);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("message").asText()).isEqualTo("pagar.me rejected the secret key");
        assertThat(get("/api/merchant", key).getBody().path("pagarMeConfigured").asBoolean(true)).isFalse();

        // Cadastro válido: verificado, cifrado e sem segredos na resposta.
        ResponseEntity<JsonNode> saved = put("/api/merchant/pagar-me", Map.of("secretKey", "sk_test_curso_bible",
                "publicKey", "pk_test_curso_bible", "webhookSecret", "whsec-bible"), auth);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode config = saved.getBody();
        assertThat(config.path("gateway").asText()).isEqualTo("PAGAR_ME");
        assertThat(config.path("pagarMeConfigured").asBoolean()).isTrue();
        assertThat(config.path("pagarMePublicKey").asText()).isEqualTo("pk_test_curso_bible");
        assertThat(config.path("pagarMeVerifiedAt").isNull()).isFalse();
        assertThat(config.path("sandbox").asBoolean()).isTrue();
        assertThat(config.path("webhookSignatureEnabled").asBoolean()).isTrue();
        assertThat(config.path("mercadoPagoConfigured").asBoolean(true)).isFalse();
        assertThat(config.path("mercadoPagoNotificationUrl").isNull()).isTrue();
        assertThat(config.toString()).doesNotContain("sk_test_curso_bible").doesNotContain("whsec-bible");
        assertThat(pagarMe.requests.subList(callsBeforeVerification, pagarMe.requests.size()))
                .anyMatch(r -> r.path().equals("/core/v5/customers"));

        // Atualizar só a public key, com a secret key vazia, mantém a chave guardada e a revalida com ela.
        pagarMe.requests.clear();
        assertThat(put("/api/merchant/pagar-me", Map.of("publicKey", "pk_test_nova"), auth).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(pagarMe.requests.get(0).authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("sk_test_curso_bible:".getBytes(StandardCharsets.UTF_8)));
        assertThat(get("/api/merchant", key).getBody().path("pagarMePublicKey").asText()).isEqualTo("pk_test_nova");

        // Agora cobra.
        ResponseEntity<JsonNode> charge = post("/api/charges",
                Map.of("invoiceId", invoiceId, "gateway", "PAGAR_ME", "method", "PIX"), auth);
        assertThat(charge.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(charge.getBody().path("status").asText()).isEqualTo("WAITING_PAYMENT");

        // Merchant Pagar.me não aceita o cadastro do Mercado Pago.
        assertThat(put("/api/merchant/mercado-pago", Map.of("accessToken", "TEST-1", "publicKey", "TEST-2"), auth)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mercadoPagoMerchantCannotRegisterPagarMeCredentials() {
        ResponseEntity<JsonNode> created = post("/api/admin/merchants", Map.of("code", "loja-mp", "name", "Loja MP"),
                Map.of("X-Admin-Key", "admin-key-test"));
        assertThat(created.getBody().path("merchant").path("gateway").asText()).isEqualTo("MERCADO_PAGO");

        ResponseEntity<JsonNode> response = put("/api/merchant/pagar-me",
                Map.of("secretKey", "sk_test_x", "publicKey", "pk_test_x"), Map.of("X-API-Key", created.getBody().path("apiKey").asText()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("message").asText()).isEqualTo("this merchant receives through MERCADO_PAGO");
    }

    private String createInvoiceFor(String apiKey, String reference) {
        ResponseEntity<JsonNode> customer = post("/api/customers", Map.of("sourceSystem", "MATER_ECCLESIAE",
                "externalReference", "ALUNO-" + reference, "name", "Maria Silva", "email", "maria@example.com",
                "document", "123.456.789-09"), Map.of("X-API-Key", apiKey));
        ResponseEntity<JsonNode> invoice = post("/api/invoices", Map.of(
                "customerId", customer.getBody().path("id").asText(),
                "sourceSystem", "MATER_ECCLESIAE",
                "externalReference", reference,
                "description", "Inscrição " + reference,
                "items", List.of(Map.of("description", "Curso", "quantity", 1, "unitAmount", 100.00))),
                Map.of("X-API-Key", apiKey));
        return invoice.getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> put(String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, httpHeaders), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String apiKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-API-Key", apiKey);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(httpHeaders), JsonNode.class);
    }

    private Map<String, Object> chargeEvent(String type, String chargeId) {
        return Map.of("id", "hook_1", "type", type, "data", Map.of("id", chargeId, "status", "paid"));
    }

    private String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String createInvoice(String reference) {
        ResponseEntity<JsonNode> customer = post("/api/customers", Map.of("sourceSystem", "MATER_ECCLESIAE",
                "externalReference", "ALUNO-" + reference, "name", "Maria Silva", "email", "maria@example.com",
                "document", "123.456.789-09"), Map.of("X-API-Key", API_KEY));
        assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<JsonNode> invoice = post("/api/invoices", Map.of(
                "customerId", customer.getBody().path("id").asText(),
                "sourceSystem", "MATER_ECCLESIAE",
                "externalReference", reference,
                "description", "Inscrição " + reference,
                "items", List.of(Map.of("description", "Curso de Verão", "quantity", 1, "unitAmount", 369.70))),
                Map.of("X-API-Key", API_KEY));
        assertThat(invoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return invoice.getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Object body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-API-Key", API_KEY);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(httpHeaders), JsonNode.class);
    }

    record Recorded(String method, String path, String authorization) {
    }

    /** Pagar.me falso: cria order com uma charge Pix pendente, consulta, e cancela/estorna por DELETE. */
    static class FakePagarMe {

        final List<Recorded> requests = new CopyOnWriteArrayList<>();
        private final ObjectMapper json = new ObjectMapper();
        private final Map<String, ObjectNode> charges = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong(1000);
        private HttpServer server;

        void start() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/", exchange -> {
                try {
                    handle(exchange);
                } catch (RuntimeException exception) {
                    exchange.sendResponseHeaders(500, -1);
                    exchange.close();
                }
            });
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void markPaid(String chargeId) {
            ObjectNode charge = charges.get(chargeId);
            charge.put("status", "paid").put("paid_at", "2026-09-25T21:00:00Z");
            ((ObjectNode) charge.path("last_transaction")).put("status", "captured");
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            requests.add(new Recorded(method, path, exchange.getRequestHeaders().getFirst("Authorization")));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && new String(Base64.getDecoder().decode(authorization.substring(6)),
                    StandardCharsets.UTF_8).startsWith("sk_test_bad")) {
                byte[] denied = "{\"message\":\"Authorization has been denied for this request.\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, denied.length);
                exchange.getResponseBody().write(denied);
                exchange.close();
                return;
            }

            JsonNode response = null;
            if ("GET".equals(method) && path.equals("/core/v5/customers")) {
                response = json.createObjectNode().set("data", json.createArrayNode());
            } else if ("POST".equals(method) && path.equals("/core/v5/orders")) {
                response = createOrder(json.readTree(body));
            } else if ("GET".equals(method) && path.startsWith("/core/v5/charges/")) {
                response = charges.get(path.substring("/core/v5/charges/".length()));
            } else if ("DELETE".equals(method) && path.startsWith("/core/v5/charges/")) {
                ObjectNode charge = charges.get(path.substring("/core/v5/charges/".length()));
                if (charge != null) {
                    charge.put("status", "canceled").put("canceled_amount", charge.path("amount").asLong());
                    ((ObjectNode) charge.path("last_transaction")).put("status", "refunded");
                }
                response = charge;
            }
            byte[] bytes = (response == null ? "{\"message\":\"not found\"}" : json.writeValueAsString(response))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response == null ? 404 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private JsonNode createOrder(JsonNode request) {
            String chargeId = "ch_" + ids.incrementAndGet();
            ObjectNode charge = json.createObjectNode().put("id", chargeId)
                    .put("amount", request.path("items").path(0).path("amount").asLong())
                    .put("status", "pending").put("canceled_amount", 0);
            charge.putObject("metadata").put("external_reference",
                    request.path("metadata").path("external_reference").asText());
            charge.putObject("last_transaction").put("status", "waiting_payment")
                    .put("qr_code", "00020126580014br.gov.bcb.pix0136fake")
                    .put("qr_code_url", "https://api.pagar.me/qr/" + chargeId)
                    .put("expires_at", "2026-09-25T21:30:00Z");
            charges.put(chargeId, charge);

            ObjectNode order = json.createObjectNode().put("id", "or_" + ids.incrementAndGet()).put("status", "pending");
            order.putArray("charges").add(charge);
            return order;
        }
    }
}
