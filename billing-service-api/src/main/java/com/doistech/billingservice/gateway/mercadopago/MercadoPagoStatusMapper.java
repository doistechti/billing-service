package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.charge.ChargeStatus;

public final class MercadoPagoStatusMapper {

    private MercadoPagoStatusMapper() {
    }

    public static ChargeStatus toChargeStatus(String status, String statusDetail) {
        if (status == null) {
            return ChargeStatus.ERROR;
        }
        return switch (status.toLowerCase()) {
            case "approved" -> ChargeStatus.PAID;
            case "pending", "in_process", "in_mediation", "authorized" -> ChargeStatus.WAITING_PAYMENT;
            case "rejected" -> ChargeStatus.REJECTED;
            case "cancelled" -> "expired".equalsIgnoreCase(statusDetail) ? ChargeStatus.EXPIRED : ChargeStatus.CANCELED;
            case "refunded", "charged_back" -> ChargeStatus.REFUNDED;
            default -> ChargeStatus.ERROR;
        };
    }
}
