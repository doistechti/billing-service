package com.doistech.billingservice.gateway.mercadopago;

/**
 * Conta dona de um access token, obtida em {@code GET /users/me}.
 */
public record MercadoPagoAccount(String id, String nickname, String siteId) {
}
