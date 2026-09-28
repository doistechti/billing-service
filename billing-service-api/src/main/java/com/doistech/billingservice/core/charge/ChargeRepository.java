package com.doistech.billingservice.core.charge;

import com.doistech.billingservice.core.payment.Gateway;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Charge> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    Optional<Charge> findFirstByMerchantIdAndGatewayAndGatewayPaymentId(
            UUID merchantId, Gateway gateway, String gatewayPaymentId);

    List<Charge> findByInvoiceIdAndMerchantIdOrderByCreatedAtDesc(UUID invoiceId, UUID merchantId);

    List<Charge> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Charge> findFirstByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Charge> findFirstByInvoiceIdAndMethodOrderByCreatedAtDesc(UUID invoiceId, ChargeMethod method);

    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, Collection<ChargeStatus> statuses);
}
