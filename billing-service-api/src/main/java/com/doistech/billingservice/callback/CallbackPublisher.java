package com.doistech.billingservice.callback;

import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.core.merchant.MerchantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Grava callbacks no outbox dentro da transação que mudou o status. O envio é feito pelo {@link CallbackDispatcher}.
 */
@Component
public class CallbackPublisher {

    private static final Logger log = LoggerFactory.getLogger(CallbackPublisher.class);

    private final CallbackDeliveryRepository repository;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;

    public CallbackPublisher(
            CallbackDeliveryRepository repository,
            MerchantRepository merchantRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String eventType, Charge charge) {
        Merchant merchant = merchantRepository.findById(charge.getMerchantId()).orElseThrow();
        if (!StringUtils.hasText(merchant.getCallbackUrl())) {
            return;
        }
        UUID deliveryId = UUID.randomUUID();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chargeId", charge.getId());
        data.put("invoiceId", charge.getInvoice().getId());
        data.put("sourceSystem", charge.getInvoice().getSourceSystem());
        data.put("externalReference", charge.getInvoice().getExternalReference());
        data.put("method", charge.getMethod());
        data.put("status", charge.getStatus());
        data.put("statusDetail", charge.getStatusDetail());
        data.put("invoiceStatus", charge.getInvoice().getStatus());
        data.put("amount", charge.getAmount());
        data.put("refundedAmount", charge.getRefundedAmount());
        data.put("gatewayPaymentId", charge.getGatewayPaymentId());
        data.put("paidAt", charge.getPaidAt());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", deliveryId);
        body.put("event", eventType);
        body.put("occurredAt", Instant.now().toString());
        body.put("data", data);

        CallbackDelivery delivery = new CallbackDelivery();
        delivery.setMerchantId(merchant.getId());
        delivery.setEventType(eventType);
        delivery.setChargeId(charge.getId());
        delivery.setTargetUrl(merchant.getCallbackUrl());
        delivery.setPayload(toJson(body));
        delivery.setStatus(CallbackStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(LocalDateTime.now());
        // O id do registro é o mesmo do payload, usado pelo receptor como chave de idempotência.
        repository.save(withId(delivery, deliveryId));
        log.info("callback queued deliveryId={} merchantId={} event={} chargeId={}",
                deliveryId, merchant.getId(), eventType, charge.getId());
    }

    private CallbackDelivery withId(CallbackDelivery delivery, UUID id) {
        delivery.assignId(id);
        return delivery;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize callback payload", exception);
        }
    }
}
