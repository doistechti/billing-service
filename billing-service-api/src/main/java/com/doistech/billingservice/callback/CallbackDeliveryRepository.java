package com.doistech.billingservice.callback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CallbackDeliveryRepository extends JpaRepository<CallbackDelivery, UUID> {

    @Query(value = """
            select * from callback_deliveries
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<CallbackDelivery> lockDue(LocalDateTime now, int limit);

    List<CallbackDelivery> findTop100ByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, CallbackStatus status);

    List<CallbackDelivery> findTop100ByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<CallbackDelivery> findByChargeIdOrderByCreatedAtAsc(UUID chargeId);

    Optional<CallbackDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);
}
