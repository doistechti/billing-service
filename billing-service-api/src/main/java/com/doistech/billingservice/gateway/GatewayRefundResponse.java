package com.doistech.billingservice.gateway;

import java.math.BigDecimal;

public record GatewayRefundResponse(
        String refundId,
        String status,
        BigDecimal amount
) {
}
