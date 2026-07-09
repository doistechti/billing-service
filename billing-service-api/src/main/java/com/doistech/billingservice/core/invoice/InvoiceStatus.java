package com.doistech.billingservice.core.invoice;

public enum InvoiceStatus {
    DRAFT,
    OPEN,
    WAITING_PAYMENT,
    PAID,
    OVERDUE,
    CANCELED
}
