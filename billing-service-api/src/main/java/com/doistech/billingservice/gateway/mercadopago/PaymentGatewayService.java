package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import java.math.BigDecimal;

public interface PaymentGatewayService {

    /**
     * Checkout Pro: cria uma preferência e devolve a URL de pagamento hospedada.
     */
    ChargeGatewayResponse createCharge(MerchantCredentials credentials, Invoice invoice);

    /**
     * Pagamento direto (Pix, cartão tokenizado ou boleto).
     */
    GatewayPaymentResponse createPayment(MerchantCredentials credentials, DirectPaymentRequest request);

    GatewayPaymentResponse getPayment(MerchantCredentials credentials, String gatewayPaymentId);

    GatewayRefundResponse refund(
            MerchantCredentials credentials, String gatewayPaymentId, BigDecimal amount, String idempotencyKey);

    GatewayPaymentResponse cancelPayment(MerchantCredentials credentials, String gatewayPaymentId);

    /**
     * Confirma que o access token é válido e devolve a conta dona dele.
     */
    MercadoPagoAccount verifyAccount(String accessToken);
}
