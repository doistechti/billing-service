package com.doistech.billingservice.core.charge;

import java.util.EnumSet;
import java.util.Set;

public enum ChargeStatus {
    CREATED,
    WAITING_PAYMENT,
    PAID,
    REJECTED,
    CANCELED,
    EXPIRED,
    REFUNDED,
    ERROR;

    public static final Set<ChargeStatus> ACTIVE = EnumSet.of(CREATED, WAITING_PAYMENT);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    /**
     * Uma charge paga só pode virar estornada; estornada é final. Evita que um evento atrasado de uma tentativa
     * recusada rebaixe uma charge já paga.
     */
    public boolean canTransitionTo(ChargeStatus next) {
        if (this == next) {
            return false;
        }
        if (this == REFUNDED) {
            return false;
        }
        if (this == PAID) {
            return next == REFUNDED;
        }
        return true;
    }
}
