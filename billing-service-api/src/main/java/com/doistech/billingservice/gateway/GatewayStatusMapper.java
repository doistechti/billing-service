package com.doistech.billingservice.gateway;

import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.mercadopago.MercadoPagoStatusMapper;
import com.doistech.billingservice.gateway.pagarme.PagarMeStatusMapper;

/**
 * Traduz o status devolvido por cada gateway para o {@link ChargeStatus} do billing.
 */
public final class GatewayStatusMapper {

    private GatewayStatusMapper() {
    }

    public static ChargeStatus toChargeStatus(Gateway gateway, String status, String statusDetail) {
        if (gateway == Gateway.PAGAR_ME) {
            return PagarMeStatusMapper.toChargeStatus(status, statusDetail);
        }
        return MercadoPagoStatusMapper.toChargeStatus(status, statusDetail);
    }
}
