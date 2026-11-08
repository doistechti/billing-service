package com.doistech.billingservice.gateway.pagarme;

import com.doistech.billingservice.core.charge.ChargeStatus;

/**
 * Status de charge do Pagar.me v5 (pending, processing, paid, overpaid, underpaid, failed, canceled).
 * {@code statusDetail} carrega o status da última transação (ex.: refunded, expired).
 *
 * <p>A confirmar na documentação oficial (SDD 10, pendências): comportamento exato de {@code underpaid},
 * {@code overpaid} e dos status de transação de expiração/estorno.
 */
public final class PagarMeStatusMapper {

    private PagarMeStatusMapper() {
    }

    public static ChargeStatus toChargeStatus(String status, String statusDetail) {
        if (status == null) {
            return ChargeStatus.ERROR;
        }
        String detail = statusDetail == null ? "" : statusDetail.toLowerCase();
        return switch (status.toLowerCase()) {
            case "paid", "overpaid" -> ChargeStatus.PAID;
            // Pagamento a menor não libera a invoice: segue aguardando e fica registrado para conferência.
            case "pending", "processing", "underpaid" -> ChargeStatus.WAITING_PAYMENT;
            case "failed" -> "expired".equals(detail) ? ChargeStatus.EXPIRED : ChargeStatus.REJECTED;
            case "canceled" -> switch (detail) {
                case "refunded", "voided" -> ChargeStatus.REFUNDED;
                case "expired" -> ChargeStatus.EXPIRED;
                default -> ChargeStatus.CANCELED;
            };
            default -> ChargeStatus.ERROR;
        };
    }
}
