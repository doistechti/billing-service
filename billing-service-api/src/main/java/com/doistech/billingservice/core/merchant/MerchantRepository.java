package com.doistech.billingservice.core.merchant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    boolean existsByCode(String code);

    List<Merchant> findAllByOrderByCreatedAtAsc();
}
