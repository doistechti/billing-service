package com.doistech.billingservice.core.merchant;

import java.util.UUID;

/**
 * Principal do Spring Security para requisições autenticadas por API key de merchant.
 */
public record AuthenticatedMerchant(UUID id, String code) {
}
