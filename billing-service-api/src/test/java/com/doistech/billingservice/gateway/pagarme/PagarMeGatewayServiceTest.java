package com.doistech.billingservice.gateway.pagarme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doistech.billingservice.config.PagarMeProperties;
import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.DirectPaymentRequest;
import com.doistech.billingservice.gateway.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.GatewayRefundResponse;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente Pagar.me contra um servidor falso. As formas das respostas seguem a API v5 como a conhecemos;
 * o que ainda precisa de confirmação oficial está nas pendências do SDD 10.
 */
class PagarMeGatewayServiceTest {

    private static final String SECRET_KEY = "sk_test_curso";
    private final ObjectMapper json = new ObjectMapper();
    private final List<Recorded> requests = new ArrayList<>();
    private HttpServer server;
    private PagarMeGatewayService gateway;
    private MerchantCredentials credentials;
    private int nextStatus = 200;
    private String nextBody;

    record Recorded(String method, String path, String authorization, String idempotencyKey, JsonNode body) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/core/v5";
        gateway = new PagarMeGatewayService(
                RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()), new PagarMeProperties(baseUrl));
        credentials = new MerchantCredentials(UUID.randomUUID(), SECRET_KEY, "pk_test_curso", null, true, null);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode body = raw.isBlank() ? json.createObjectNode() : json.readTree(raw);
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"), body));
        byte[] bytes = nextBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(nextStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private DirectPaymentRequest request(ChargeMethod method, DirectPaymentRequest.Card card,
                                         DirectPaymentRequest.Address address) {
        return new DirectPaymentRequest(method, new BigDecimal("369.70"), "Inscrição Curso de Verão",
                "INVOICE:abc|SOURCE:MATER_ECCLESIAE|REF:CURSO-1-ED-2-INSC-3", "idem-1",
                OffsetDateTime.now().plusMinutes(30),
                new DirectPaymentRequest.Payer("aluno@example.com", "Maria", "Silva", "CPF", "123.456.789-09", address),
                card);
    }

    @Test
    void pixOrderIsSentInCentsWithBasicAuthAndParsedBack() {
        nextBody = """
                {"id":"or_1","status":"pending",
                 "metadata":{"external_reference":"INVOICE:abc|SOURCE:MATER_ECCLESIAE|REF:CURSO-1-ED-2-INSC-3"},
                 "charges":[{"id":"ch_pix","amount":36970,"status":"pending",
                 "last_transaction":{"status":"waiting_payment","qr_code":"00020126pix",
                 "qr_code_url":"https://api.pagar.me/qr/1","expires_at":"2026-09-25T21:30:00Z"}}]}""";

        GatewayPaymentResponse payment = gateway.createPayment(credentials, request(ChargeMethod.PIX, null, null));

        Recorded sent = requests.get(0);
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/core/v5/orders");
        assertThat(sent.authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)));
        assertThat(sent.idempotencyKey()).isEqualTo("idem-1");
        assertThat(sent.body().path("items").path(0).path("amount").asLong()).isEqualTo(36970);
        assertThat(sent.body().path("customer").path("document").asText()).isEqualTo("12345678909");
        assertThat(sent.body().path("customer").path("document_type").asText()).isEqualTo("CPF");
        assertThat(sent.body().path("customer").path("type").asText()).isEqualTo("individual");
        assertThat(sent.body().path("customer").path("name").asText()).isEqualTo("Maria Silva");
        assertThat(sent.body().path("payments").path(0).path("payment_method").asText()).isEqualTo("pix");
        assertThat(sent.body().path("payments").path(0).path("pix").path("expires_in").asLong()).isBetween(1700L, 1800L);
        assertThat(sent.body().path("metadata").path("external_reference").asText()).startsWith("INVOICE:abc|");

        assertThat(payment.gateway()).isEqualTo(Gateway.PAGAR_ME);
        assertThat(payment.gatewayPaymentId()).isEqualTo("ch_pix");
        assertThat(payment.gatewayStatus()).isEqualTo("pending");
        assertThat(payment.statusDetail()).isEqualTo("waiting_payment");
        assertThat(payment.amount()).isEqualByComparingTo("369.70");
        assertThat(payment.pixQrCode()).isEqualTo("00020126pix");
        assertThat(payment.ticketUrl()).isEqualTo("https://api.pagar.me/qr/1");
        // 21:30 UTC = 18:30 em Brasília.
        assertThat(payment.expiresAt()).isEqualTo("2026-09-25T18:30:00");
        assertThat(payment.externalReference()).isEqualTo("INVOICE:abc|SOURCE:MATER_ECCLESIAE|REF:CURSO-1-ED-2-INSC-3");
    }

    @Test
    void cardIsSentTokenizedAndApprovedChargeIsPaid() {
        nextBody = """
                {"id":"or_2","status":"paid","charges":[{"id":"ch_card","amount":36970,"status":"paid",
                 "paid_at":"2026-09-25T21:00:00Z","last_transaction":{"status":"captured","installments":10}}]}""";

        GatewayPaymentResponse payment = gateway.createPayment(credentials, request(ChargeMethod.CREDIT_CARD,
                new DirectPaymentRequest.Card("tok_abc", "visa", null, 10), null));

        JsonNode card = requests.get(0).body().path("payments").path(0);
        assertThat(card.path("payment_method").asText()).isEqualTo("credit_card");
        assertThat(card.path("credit_card").path("card_token").asText()).isEqualTo("tok_abc");
        assertThat(card.path("credit_card").path("installments").asInt()).isEqualTo(10);
        assertThat(payment.gatewayStatus()).isEqualTo("paid");
        assertThat(payment.installments()).isEqualTo(10);
        assertThat(payment.paidAt()).isEqualTo("2026-09-25T18:00:00");
    }

    @Test
    void declinedCardKeepsTransactionStatusAsDetail() {
        nextBody = """
                {"id":"or_3","status":"failed","charges":[{"id":"ch_fail","amount":36970,"status":"failed",
                 "last_transaction":{"status":"not_authorized"}}]}""";

        GatewayPaymentResponse payment = gateway.createPayment(credentials, request(ChargeMethod.CREDIT_CARD,
                new DirectPaymentRequest.Card("tok_x", "visa", null, 1), null));

        assertThat(payment.gatewayStatus()).isEqualTo("failed");
        assertThat(payment.statusDetail()).isEqualTo("not_authorized");
    }

    @Test
    void boletoSendsAddressAndReturnsLineAndPdf() {
        nextBody = """
                {"id":"or_4","status":"pending","charges":[{"id":"ch_bol","amount":36970,"status":"pending",
                 "last_transaction":{"status":"waiting_payment","pdf":"https://api.pagar.me/boleto.pdf",
                 "line":"23790.00009 12345","barcode":"2379100000","due_at":"2026-09-28T02:59:59Z"}}]}""";

        GatewayPaymentResponse payment = gateway.createPayment(credentials, request(ChargeMethod.BOLETO, null,
                new DirectPaymentRequest.Address("01310-100", "Av. Paulista", "1000", "Bela Vista", "São Paulo", "SP")));

        JsonNode sent = requests.get(0).body();
        assertThat(sent.path("payments").path(0).path("payment_method").asText()).isEqualTo("boleto");
        assertThat(sent.path("payments").path(0).path("boleto").path("due_at").asText()).endsWith("Z");
        assertThat(sent.path("customer").path("address").path("zip_code").asText()).isEqualTo("01310100");
        assertThat(sent.path("customer").path("address").path("line_1").asText()).isEqualTo("1000, Av. Paulista, Bela Vista");
        assertThat(sent.path("customer").path("address").path("country").asText()).isEqualTo("BR");
        assertThat(payment.boletoDigitableLine()).isEqualTo("23790.00009 12345");
        assertThat(payment.boletoBarcode()).isEqualTo("2379100000");
        assertThat(payment.ticketUrl()).isEqualTo("https://api.pagar.me/boleto.pdf");
    }

    @Test
    void getPaymentReadsTheChargeAndItsOrderMetadata() {
        nextBody = """
                {"id":"ch_pix","amount":36970,"status":"paid","paid_at":"2026-09-25T21:00:00Z","canceled_amount":0,
                 "last_transaction":{"status":"captured"},
                 "order":{"id":"or_1","metadata":{"external_reference":"INVOICE:abc|SOURCE:X|REF:1"}}}""";

        GatewayPaymentResponse payment = gateway.getPayment(credentials, "ch_pix");

        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).isEqualTo("/core/v5/charges/ch_pix");
        assertThat(requests.get(0).idempotencyKey()).isNull();
        assertThat(payment.gatewayStatus()).isEqualTo("paid");
        assertThat(payment.externalReference()).isEqualTo("INVOICE:abc|SOURCE:X|REF:1");
        assertThat(payment.refundedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void partialRefundSendsAmountInCentsOnDelete() {
        nextBody = """
                {"id":"ch_card","status":"paid","amount":36970,"canceled_amount":10000,
                 "last_transaction":{"status":"partial_refunded"}}""";

        GatewayRefundResponse refund = gateway.refund(credentials, "ch_card", new BigDecimal("100.00"), "refund-1");

        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(requests.get(0).path()).isEqualTo("/core/v5/charges/ch_card");
        assertThat(requests.get(0).body().path("amount").asLong()).isEqualTo(10000);
        assertThat(requests.get(0).idempotencyKey()).isEqualTo("refund-1");
        assertThat(refund.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancelDeletesTheChargeWithoutAmount() {
        nextBody = """
                {"id":"ch_pix","status":"canceled","amount":36970,"canceled_amount":36970,
                 "last_transaction":{"status":"canceled"}}""";

        GatewayPaymentResponse canceled = gateway.cancelPayment(credentials, "ch_pix");

        assertThat(requests.get(0).method()).isEqualTo("DELETE");
        assertThat(requests.get(0).body().has("amount")).isFalse();
        assertThat(canceled.gatewayStatus()).isEqualTo("canceled");
    }

    @Test
    void validationErrorsSurfaceFieldMessages() {
        nextStatus = 422;
        nextBody = "{\"message\":\"The request is invalid.\",\"errors\":{\"customer.document\":[\"invalid document\"]}}";

        assertThatThrownBy(() -> gateway.createPayment(credentials, request(ChargeMethod.PIX, null, null)))
                .isInstanceOf(GatewayIntegrationException.class)
                .hasMessageContaining("customer.document: invalid document")
                .satisfies(e -> assertThat(((GatewayIntegrationException) e).isTransient()).isFalse());
    }

    @Test
    void serverErrorIsTransientAndUnauthorizedIsNot() {
        nextStatus = 503;
        nextBody = "{}";
        assertThatThrownBy(() -> gateway.getPayment(credentials, "ch_x"))
                .satisfies(e -> assertThat(((GatewayIntegrationException) e).isTransient()).isTrue());

        nextStatus = 401;
        nextBody = "{\"message\":\"Authorization has been denied for this request.\"}";
        assertThatThrownBy(() -> gateway.verifyAccount("sk_bad"))
                .hasMessageContaining("invalid secret key")
                .satisfies(e -> assertThat(((GatewayIntegrationException) e).isTransient()).isFalse());
    }

    @Test
    void verifyAccountProbesAnAuthenticatedEndpoint() {
        nextBody = "{\"data\":[],\"paging\":{\"total\":0}}";

        assertThat(gateway.verifyAccount(SECRET_KEY)).isNotNull();

        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).path()).startsWith("/core/v5/customers");
    }

    @Test
    void hostedCheckoutIsNotSupportedYet() {
        assertThatThrownBy(() -> gateway.createCharge(credentials, null))
                .isInstanceOf(GatewayIntegrationException.class)
                .hasMessageContaining("PIX, BOLETO or CREDIT_CARD");
    }

    @Test
    void centsConversionRoundsHalfUpWithoutFloatingPointError() {
        assertThat(PagarMeGatewayService.toCents(new BigDecimal("289.90"))).isEqualTo(28990);
        assertThat(PagarMeGatewayService.toCents(new BigDecimal("0.10"))).isEqualTo(10);
        assertThat(PagarMeGatewayService.toCents(new BigDecimal("10.005"))).isEqualTo(1001);
        assertThat(PagarMeGatewayService.fromCents(36970)).isEqualByComparingTo("369.70");
    }
}
