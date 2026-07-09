package com.doistech.billingservice.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeRepository;
import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentRepository;
import com.doistech.billingservice.gateway.mercadopago.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.mercadopago.PaymentGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private MercadoPagoWebhookService webhookService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldProcessApprovedWebhookWithoutDuplicatingPayment() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.WAITING_PAYMENT);

        Charge charge = new Charge();
        charge.setInvoice(invoice);
        charge.setStatus(ChargeStatus.WAITING_PAYMENT);

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayService.getPayment("123456")).thenReturn(new GatewayPaymentResponse(
                Gateway.MERCADO_PAGO,
                "123456",
                "approved",
                new BigDecimal("150.00"),
                LocalDateTime.of(2026, 7, 9, 10, 30),
                "INVOICE:%s|SOURCE:MATER_ECCLESIAE|REF:ALUNO-123".formatted(invoiceId),
                objectMapper.createObjectNode().put("status", "approved")
        ));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(chargeRepository.findFirstByInvoiceIdOrderByCreatedAtDesc(invoiceId)).thenReturn(Optional.of(charge));
        when(paymentRepository.existsByGatewayAndGatewayPaymentId(Gateway.MERCADO_PAGO, "123456")).thenReturn(false);

        var payload = objectMapper.createObjectNode();
        payload.putObject("data").put("id", "123456");

        webhookService.receive(payload);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals("123456", paymentCaptor.getValue().getGatewayPaymentId());
        assertEquals(ChargeStatus.PAID, charge.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }
}
