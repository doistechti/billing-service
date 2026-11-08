package com.doistech.billingservice.gateway;

import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.payment.Gateway;
import java.math.BigDecimal;

public interface PaymentGatewayService {

    /**
     * Gateway que esta implementação atende. O {@link PaymentGatewayRouter} escolhe por ele.
     */
    Gateway gateway();

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
    GatewayAccount verifyAccount(String accessToken);
}
