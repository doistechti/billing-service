package com.doistech.billingservice.core.invoice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findById(UUID id);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findByIdAndMerchantId(UUID id, UUID merchantId);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findByMerchantIdAndSourceSystemAndExternalReference(
            UUID merchantId, String sourceSystem, String externalReference);

    boolean existsByMerchantIdAndSourceSystemAndExternalReference(
            UUID merchantId, String sourceSystem, String externalReference);

    default Optional<Invoice> findDetailedById(UUID id) {
        return findById(id);
    }
}
