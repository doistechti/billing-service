package com.doistech.billingservice.core.merchant;

import java.util.UUID;

/**
 * Credenciais Mercado Pago de um merchant, já decifradas, usadas apenas em memória pelo gateway.
 */
public record MerchantCredentials(
        UUID merchantId,
        String accessToken,
        String publicKey,
        String webhookSecret,
        boolean sandbox,
        String notificationUrl
) {

    @Override
    public String toString() {
        return "MerchantCredentials[merchantId=%s, sandbox=%s]".formatted(merchantId, sandbox);
    }
}
