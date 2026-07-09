package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.core.invoice.Invoice;

public interface PaymentGatewayService {

    ChargeGatewayResponse createCharge(Invoice invoice);

    GatewayPaymentResponse getPayment(String gatewayPaymentId);
}
