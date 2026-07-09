package com.doistech.billingservice.core.charge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    List<Charge> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Charge> findFirstByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, List<ChargeStatus> statuses);
}
