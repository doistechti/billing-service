package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.payment.Gateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayPaymentResponse(
        Gateway gateway,
        String gatewayPaymentId,
        String gatewayStatus,
        BigDecimal amount,
        LocalDateTime paidAt,
        String externalReference,
        JsonNode rawResponse
) {
}
