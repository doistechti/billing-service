package com.doistech.billingservice.core.charge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.PaymentGatewayRouter;
import com.doistech.billingservice.gateway.PaymentGatewayService;
import com.doistech.billingservice.shared.BusinessRuleException;
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

    @Mock
    private PaymentGatewayRouter gatewayRouter;

    @Mock
    private MerchantService merchantService;

    @Mock
    private ChargeStatusSynchronizer synchronizer;

    @InjectMocks
    private ChargeService chargeService;

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @Test
    void shouldRejectChargeCreationForPaidInvoice() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        invoice.setStatus(InvoiceStatus.PAID);

        when(merchantService.gatewayOf(MERCHANT_ID)).thenReturn(Gateway.MERCADO_PAGO);
        when(gatewayRouter.forGateway(Gateway.MERCADO_PAGO)).thenReturn(paymentGatewayService);
        when(invoiceRepository.findByIdAndMerchantId(invoiceId, MERCHANT_ID)).thenReturn(Optional.of(invoice));

        assertThrows(BusinessRuleException.class, () -> chargeService.create(
                MERCHANT_ID, new CreateChargeRequest(invoiceId, Gateway.MERCADO_PAGO), null));
    }

    @Test
    void shouldRejectWhenInvoiceAlreadyHasActiveCharge() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        invoice.setStatus(InvoiceStatus.OPEN);

        when(merchantService.gatewayOf(MERCHANT_ID)).thenReturn(Gateway.MERCADO_PAGO);
        when(gatewayRouter.forGateway(Gateway.MERCADO_PAGO)).thenReturn(paymentGatewayService);
        when(invoiceRepository.findByIdAndMerchantId(invoiceId, MERCHANT_ID)).thenReturn(Optional.of(invoice));
        when(chargeRepository.existsByInvoiceIdAndStatusIn(invoiceId, ChargeStatus.ACTIVE))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> chargeService.create(
                MERCHANT_ID, new CreateChargeRequest(invoiceId, Gateway.MERCADO_PAGO), null));
    }

    @Test
    void shouldRejectWhenRequestedGatewayDiffersFromMerchantGateway() {
        UUID invoiceId = UUID.randomUUID();
        when(merchantService.gatewayOf(MERCHANT_ID)).thenReturn(Gateway.MERCADO_PAGO);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> chargeService.create(
                MERCHANT_ID, new CreateChargeRequest(invoiceId, Gateway.PAGAR_ME), null));

        assertTrue(exception.getMessage().startsWith("gateway mismatch"));
    }
}
