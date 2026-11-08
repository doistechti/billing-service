package com.doistech.billingservice.gateway.pagarme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.GatewayStatusMapper;
import org.junit.jupiter.api.Test;

class PagarMeStatusMapperTest {

    @Test
    void mapsChargeStatuses() {
        assertEquals(ChargeStatus.PAID, PagarMeStatusMapper.toChargeStatus("paid", "captured"));
        assertEquals(ChargeStatus.PAID, PagarMeStatusMapper.toChargeStatus("overpaid", null));
        assertEquals(ChargeStatus.WAITING_PAYMENT, PagarMeStatusMapper.toChargeStatus("pending", "waiting_payment"));
        assertEquals(ChargeStatus.WAITING_PAYMENT, PagarMeStatusMapper.toChargeStatus("processing", null));
        assertEquals(ChargeStatus.REJECTED, PagarMeStatusMapper.toChargeStatus("failed", "not_authorized"));
        assertEquals(ChargeStatus.CANCELED, PagarMeStatusMapper.toChargeStatus("canceled", "waiting_payment"));
    }

    @Test
    void underpaidNeverReleasesTheInvoice() {
        assertEquals(ChargeStatus.WAITING_PAYMENT, PagarMeStatusMapper.toChargeStatus("underpaid", null));
    }

    @Test
    void distinguishesExpiredAndRefundedByTransactionStatus() {
        assertEquals(ChargeStatus.EXPIRED, PagarMeStatusMapper.toChargeStatus("failed", "expired"));
        assertEquals(ChargeStatus.EXPIRED, PagarMeStatusMapper.toChargeStatus("canceled", "expired"));
        assertEquals(ChargeStatus.REFUNDED, PagarMeStatusMapper.toChargeStatus("canceled", "refunded"));
        assertEquals(ChargeStatus.REFUNDED, PagarMeStatusMapper.toChargeStatus("canceled", "voided"));
    }

    @Test
    void unknownOrMissingStatusIsError() {
        assertEquals(ChargeStatus.ERROR, PagarMeStatusMapper.toChargeStatus("whatever", null));
        assertEquals(ChargeStatus.ERROR, PagarMeStatusMapper.toChargeStatus(null, null));
    }

    @Test
    void dispatchesByGateway() {
        assertEquals(ChargeStatus.PAID, GatewayStatusMapper.toChargeStatus(Gateway.PAGAR_ME, "paid", null));
        assertEquals(ChargeStatus.PAID, GatewayStatusMapper.toChargeStatus(Gateway.MERCADO_PAGO, "approved", null));
        // "approved" não existe no Pagar.me e "paid" não existe no Mercado Pago: cada gateway usa o seu mapa.
        assertEquals(ChargeStatus.ERROR, GatewayStatusMapper.toChargeStatus(Gateway.PAGAR_ME, "approved", null));
        assertEquals(ChargeStatus.ERROR, GatewayStatusMapper.toChargeStatus(Gateway.MERCADO_PAGO, "paid", null));
    }
}
