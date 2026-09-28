package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.payment.Gateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayPaymentResponse(
        Gateway gateway,
        String gatewayPaymentId,
        String gatewayStatus,
        String statusDetail,
        BigDecimal amount,
        BigDecimal refundedAmount,
        LocalDateTime paidAt,
        String externalReference,
        Integer installments,
        String pixQrCode,
        String pixQrCodeBase64,
        String ticketUrl,
        String boletoBarcode,
        String boletoDigitableLine,
        LocalDateTime expiresAt,
        JsonNode rawResponse
) {

    public GatewayPaymentResponse(
            Gateway gateway,
            String gatewayPaymentId,
            String gatewayStatus,
            BigDecimal amount,
            LocalDateTime paidAt,
            String externalReference,
            JsonNode rawResponse
    ) {
        this(gateway, gatewayPaymentId, gatewayStatus, null, amount, BigDecimal.ZERO, paidAt, externalReference,
                null, null, null, null, null, null, null, rawResponse);
    }
}
