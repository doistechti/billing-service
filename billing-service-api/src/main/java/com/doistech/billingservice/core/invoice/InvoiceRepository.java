package com.doistech.billingservice.core.invoice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findById(UUID id);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findBySourceSystemAndExternalReference(String sourceSystem, String externalReference);

    default Optional<Invoice> findDetailedById(UUID id) {
        return findById(id);
    }
}
