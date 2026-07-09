package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.payment.Gateway;
import java.math.BigDecimal;

public record ChargeGatewayResponse(
        Gateway gateway,
        String gatewayPreferenceId,
        String paymentUrl,
        BigDecimal amount,
        ChargeStatus status
) {
}
