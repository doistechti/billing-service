package com.doistech.billingservice.gateway.mercadopago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.doistech.billingservice.core.charge.ChargeStatus;
import org.junit.jupiter.api.Test;

class MercadoPagoStatusMapperTest {

    @Test
    void shouldMapMercadoPagoStatuses() {
        assertEquals(ChargeStatus.PAID, MercadoPagoStatusMapper.toChargeStatus("approved", "accredited"));
        assertEquals(ChargeStatus.WAITING_PAYMENT, MercadoPagoStatusMapper.toChargeStatus("pending", "pending_waiting_transfer"));
        assertEquals(ChargeStatus.WAITING_PAYMENT, MercadoPagoStatusMapper.toChargeStatus("in_process", null));
        assertEquals(ChargeStatus.REJECTED, MercadoPagoStatusMapper.toChargeStatus("rejected", "cc_rejected_insufficient_amount"));
        assertEquals(ChargeStatus.EXPIRED, MercadoPagoStatusMapper.toChargeStatus("cancelled", "expired"));
        assertEquals(ChargeStatus.CANCELED, MercadoPagoStatusMapper.toChargeStatus("cancelled", "by_collector"));
        assertEquals(ChargeStatus.REFUNDED, MercadoPagoStatusMapper.toChargeStatus("refunded", null));
        assertEquals(ChargeStatus.REFUNDED, MercadoPagoStatusMapper.toChargeStatus("charged_back", null));
        assertEquals(ChargeStatus.ERROR, MercadoPagoStatusMapper.toChargeStatus("weird", null));
        assertEquals(ChargeStatus.ERROR, MercadoPagoStatusMapper.toChargeStatus(null, null));
    }
}
