package com.doistech.billingservice.gateway;

import com.doistech.billingservice.core.charge.ChargeMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DirectPaymentRequest(
        ChargeMethod method,
        BigDecimal amount,
        String description,
        String externalReference,
        String idempotencyKey,
        OffsetDateTime expiresAt,
        Payer payer,
        Card card
) {

    public record Payer(
            String email,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            Address address,
            String phone
    ) {

        /** Compatibilidade: pagador sem telefone. */
        public Payer(String email, String firstName, String lastName, String documentType, String documentNumber, Address address) {
            this(email, firstName, lastName, documentType, documentNumber, address, null);
        }
    }

    public record Address(
            String zipCode,
            String street,
            String number,
            String neighborhood,
            String city,
            String state
    ) {
    }

    public record Card(
            String token,
            String paymentMethodId,
            String issuerId,
            Integer installments
    ) {

        @Override
        public String toString() {
            return "Card[paymentMethodId=%s, installments=%s]".formatted(paymentMethodId, installments);
        }
    }
}
