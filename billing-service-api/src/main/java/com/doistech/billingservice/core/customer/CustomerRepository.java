package com.doistech.billingservice.core.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Customer> findByMerchantIdAndSourceSystemAndExternalReference(
            UUID merchantId, String sourceSystem, String externalReference);

    boolean existsByMerchantIdAndSourceSystemAndExternalReference(
            UUID merchantId, String sourceSystem, String externalReference);
}
