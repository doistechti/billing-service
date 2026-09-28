package com.doistech.billingservice.callback;

import com.doistech.billingservice.config.CallbackProperties;
import com.doistech.billingservice.shared.NotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações transacionais do outbox de callbacks.
 */
@Component
public class CallbackQueue {

    private static final Duration LEASE = Duration.ofMinutes(2);

    private final CallbackDeliveryRepository repository;
    private final CallbackProperties properties;

    public CallbackQueue(CallbackDeliveryRepository repository, CallbackProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Reserva os callbacks vencidos adiando o próximo horário (lease), para que outra instância não os envie ao
     * mesmo tempo. Se o processo cair no meio do envio, o callback volta a ficar disponível quando o lease expira.
     */
    @Transactional
    public List<ClaimedCallback> claimDue() {
        LocalDateTime now = LocalDateTime.now();
        List<CallbackDelivery> due = repository.lockDue(now, properties.batchSize());
        for (CallbackDelivery delivery : due) {
            delivery.setNextAttemptAt(now.plus(LEASE));
        }
        repository.saveAll(due);
        return due.stream()
                .map(delivery -> new ClaimedCallback(
                        delivery.getId(), delivery.getMerchantId(), delivery.getEventType(), delivery.getPayload()))
                .toList();
    }

    @Transactional
    public void markDelivered(UUID id, int statusCode) {
        CallbackDelivery delivery = repository.findById(id).orElseThrow();
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setStatus(CallbackStatus.DELIVERED);
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(null);
        delivery.setDeliveredAt(LocalDateTime.now());
        repository.save(delivery);
    }

    @Transactional
    public void markFailed(UUID id, Integer statusCode, String error) {
        CallbackDelivery delivery = repository.findById(id).orElseThrow();
        int attempts = delivery.getAttempts() + 1;
        delivery.setAttempts(attempts);
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 1000)));
        if (attempts >= properties.maxAttempts()) {
            delivery.setStatus(CallbackStatus.FAILED);
        } else {
            long multiplier = 1L << Math.min(attempts - 1, 20);
            delivery.setNextAttemptAt(LocalDateTime.now().plus(properties.initialBackoff().multipliedBy(multiplier)));
        }
        repository.save(delivery);
    }

    @Transactional(readOnly = true)
    public List<CallbackDelivery> list(UUID merchantId, CallbackStatus status) {
        return status == null
                ? repository.findTop100ByMerchantIdOrderByCreatedAtDesc(merchantId)
                : repository.findTop100ByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status);
    }

    @Transactional
    public CallbackDelivery redeliver(UUID merchantId, UUID id) {
        CallbackDelivery delivery = repository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotFoundException("callback not found"));
        delivery.setStatus(CallbackStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(LocalDateTime.now());
        return repository.save(delivery);
    }

    public record ClaimedCallback(UUID id, UUID merchantId, String eventType, String payload) {
    }
}
