package com.doistech.billingservice.core.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Payment> findByGatewayAndGatewayPaymentId(Gateway gateway, String gatewayPaymentId);

    boolean existsByGatewayAndGatewayPaymentId(Gateway gateway, String gatewayPaymentId);
}
