package com.doistech.billingservice.core.charge;

public enum ChargeMethod {
    CHECKOUT_PRO,
    PIX,
    CREDIT_CARD,
    BOLETO;

    public boolean isDirect() {
        return this != CHECKOUT_PRO;
    }
}
