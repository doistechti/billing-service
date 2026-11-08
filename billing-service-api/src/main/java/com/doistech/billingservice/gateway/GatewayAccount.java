package com.doistech.billingservice.gateway;

/**
 * Conta dona de um access token, obtida em {@code GET /users/me}.
 */
public record GatewayAccount(String id, String nickname, String siteId) {
}
