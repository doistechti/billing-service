package com.doistech.billingservice.core.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findBySourceSystemAndExternalReference(String sourceSystem, String externalReference);
}
