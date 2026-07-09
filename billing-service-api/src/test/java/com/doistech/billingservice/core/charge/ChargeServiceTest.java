package com.doistech.billingservice.core.charge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.mercadopago.PaymentGatewayService;
import com.doistech.billingservice.shared.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @InjectMocks
    private ChargeService chargeService;

    @Test
    void shouldRejectChargeCreationForPaidInvoice() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        invoice.setStatus(InvoiceStatus.PAID);

        when(invoiceRepository.findDetailedById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(BusinessRuleException.class, () -> chargeService.create(
                new CreateChargeRequest(invoiceId, Gateway.MERCADO_PAGO)));
    }

    @Test
    void shouldRejectWhenInvoiceAlreadyHasActiveCharge() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        invoice.setStatus(InvoiceStatus.OPEN);

        when(invoiceRepository.findDetailedById(invoiceId)).thenReturn(Optional.of(invoice));
        when(chargeRepository.existsByInvoiceIdAndStatusIn(invoiceId, List.of(ChargeStatus.CREATED, ChargeStatus.WAITING_PAYMENT)))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> chargeService.create(
                new CreateChargeRequest(invoiceId, Gateway.MERCADO_PAGO)));
    }
}
