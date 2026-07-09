package com.doistech.billingservice.core.charge;

public enum ChargeStatus {
    CREATED,
    WAITING_PAYMENT,
    PAID,
    REJECTED,
    CANCELED,
    ERROR
}
