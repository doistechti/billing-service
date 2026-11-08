package com.doistech.billingservice.webhook;

import com.doistech.billingservice.core.payment.Gateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra os eventos recebidos em transação própria, para que o histórico sobreviva a falhas do processamento.
 */
@Component
public class WebhookEventRecorder {

    private final WebhookEventRepository repository;

    public WebhookEventRecorder(WebhookEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(Gateway gateway, UUID merchantId, String eventId, String eventType, String paymentId, JsonNode payload) {
        WebhookEvent event = new WebhookEvent();
        event.setMerchantId(merchantId);
        event.setGateway(gateway);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setGatewayPaymentId(paymentId);
        event.setPayload(payload);
        event.setProcessed(false);
        return repository.save(event).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            event.setErrorMessage(null);
            repository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String error) {
        repository.findById(eventId).ifPresent(event -> {
            event.setErrorMessage(error);
            repository.save(event);
        });
    }
}
