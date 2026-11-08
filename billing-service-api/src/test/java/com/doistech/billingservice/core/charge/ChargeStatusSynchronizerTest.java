package com.doistech.billingservice.core.charge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.callback.CallbackPublisher;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentRepository;
import com.doistech.billingservice.gateway.GatewayPaymentResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeStatusSynchronizerTest {

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CallbackPublisher callbackPublisher;

    @InjectMocks
    private ChargeStatusSynchronizer synchronizer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMarkChargeAndInvoicePaidAndRecordPayment() {
        Charge charge = charge(ChargeStatus.WAITING_PAYMENT, InvoiceStatus.WAITING_PAYMENT);
        when(paymentRepository.findByGatewayAndGatewayPaymentId(Gateway.MERCADO_PAGO, "123456")).thenReturn(Optional.empty());

        boolean changed = synchronizer.apply(charge, payment("approved", null));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals("123456", paymentCaptor.getValue().getGatewayPaymentId());
        assertEquals(true, changed);
        assertEquals(ChargeStatus.PAID, charge.getStatus());
        assertEquals(InvoiceStatus.PAID, charge.getInvoice().getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 9, 10, 30), charge.getInvoice().getPaidAt());
        verify(callbackPublisher).publish("charge.paid", charge);
    }

    @Test
    void shouldNotDowngradePaidChargeWhenLateRejectionArrives() {
        Charge charge = charge(ChargeStatus.PAID, InvoiceStatus.PAID);
        when(paymentRepository.findByGatewayAndGatewayPaymentId(Gateway.MERCADO_PAGO, "123456")).thenReturn(Optional.empty());

        boolean changed = synchronizer.apply(charge, payment("rejected", "cc_rejected_other_reason"));

        assertFalse(changed);
        assertEquals(ChargeStatus.PAID, charge.getStatus());
        assertEquals(InvoiceStatus.PAID, charge.getInvoice().getStatus());
        verify(callbackPublisher, never()).publish(anyString(), any());
    }

    @Test
    void shouldReopenInvoiceWhenPixExpires() {
        Charge charge = charge(ChargeStatus.WAITING_PAYMENT, InvoiceStatus.WAITING_PAYMENT);
        when(paymentRepository.findByGatewayAndGatewayPaymentId(Gateway.MERCADO_PAGO, "123456")).thenReturn(Optional.empty());

        synchronizer.apply(charge, payment("cancelled", "expired"));

        assertEquals(ChargeStatus.EXPIRED, charge.getStatus());
        assertEquals(InvoiceStatus.OPEN, charge.getInvoice().getStatus());
        verify(callbackPublisher).publish("charge.expired", charge);
    }

    @Test
    void shouldRefundPartiallyThenTotally() {
        Charge charge = charge(ChargeStatus.PAID, InvoiceStatus.PAID);

        synchronizer.applyRefund(charge, new BigDecimal("50.00"));
        assertEquals(ChargeStatus.PAID, charge.getStatus());
        assertEquals(new BigDecimal("50.00"), charge.getRefundedAmount());

        synchronizer.applyRefund(charge, new BigDecimal("100.00"));
        assertEquals(ChargeStatus.REFUNDED, charge.getStatus());
        assertEquals(InvoiceStatus.REFUNDED, charge.getInvoice().getStatus());
    }

    private Charge charge(ChargeStatus chargeStatus, InvoiceStatus invoiceStatus) {
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        invoice.setStatus(invoiceStatus);

        Charge charge = new Charge();
        ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
        charge.setMerchantId(UUID.randomUUID());
        charge.setInvoice(invoice);
        charge.setAmount(new BigDecimal("150.00"));
        charge.setStatus(chargeStatus);
        return charge;
    }

    private GatewayPaymentResponse payment(String status, String statusDetail) {
        return new GatewayPaymentResponse(
                Gateway.MERCADO_PAGO,
                "123456",
                status,
                statusDetail,
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "approved".equals(status) ? LocalDateTime.of(2026, 7, 9, 10, 30) : null,
                "INVOICE:x|SOURCE:MATER_ECCLESIAE|REF:ALUNO-123",
                null, null, null, null, null, null, null,
                objectMapper.createObjectNode().put("status", status));
    }
}
