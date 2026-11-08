package com.doistech.billingservice.webhook;

import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.mercadopago.MercadoPagoSignatureVerifier;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.doistech.billingservice.shared.InvalidSignatureException;
import com.doistech.billingservice.shared.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MercadoPagoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookService.class);

    private final MerchantService merchantService;
    private final WebhookEventRecorder recorder;
    private final WebhookPaymentProcessor processor;

    public MercadoPagoWebhookService(
            MerchantService merchantService,
            WebhookEventRecorder recorder,
            WebhookPaymentProcessor processor
    ) {
        this.merchantService = merchantService;
        this.recorder = recorder;
        this.processor = processor;
    }

    /**
     * Processa uma notificação. Lança {@link GatewayIntegrationException} transitória quando o Mercado Pago deve
     * reenviar; nos demais casos registra o erro e responde normalmente.
     */
    public void receive(UUID merchantId, JsonNode payload, WebhookHeaders headers) {
        if (merchantService.getById(merchantId).getGateway() != Gateway.MERCADO_PAGO) {
            throw new NotFoundException("merchant not found");
        }

        String paymentId = Optional.ofNullable(headers.queryDataId()).orElseGet(() -> extractPaymentId(payload));
        String eventType = firstNonBlank(textAt(payload, "type"), textAt(payload, "topic"), textAt(payload, "action"));

        Optional<String> secret = merchantService.webhookSecret(merchantId);
        if (secret.isPresent() && !MercadoPagoSignatureVerifier.isValid(
                secret.get(), headers.signature(), headers.requestId(), paymentId)) {
            log.warn("mercado pago webhook rejected: invalid signature merchantId={} paymentId={}", merchantId, paymentId);
            throw new InvalidSignatureException("invalid webhook signature");
        }

        UUID eventRecordId = recorder.record(Gateway.MERCADO_PAGO, merchantId, textAt(payload, "id"), eventType, paymentId, payload);
        log.info("mercado pago webhook received merchantId={} eventRecordId={} eventType={} paymentId={}",
                merchantId, eventRecordId, eventType, paymentId);

        if (!isPaymentEvent(eventType) || !StringUtils.hasText(paymentId)) {
            recorder.markProcessed(eventRecordId);
            log.info("mercado pago webhook ignored eventRecordId={} eventType={}", eventRecordId, eventType);
            return;
        }

        try {
            processor.process(merchantId, paymentId);
            recorder.markProcessed(eventRecordId);
        } catch (GatewayIntegrationException exception) {
            recorder.markFailed(eventRecordId, exception.getMessage());
            if (exception.isTransient()) {
                log.error("mercado pago webhook will be retried eventRecordId={} paymentId={} error={}",
                        eventRecordId, paymentId, exception.getMessage());
                throw exception;
            }
            log.warn("mercado pago webhook not processed eventRecordId={} paymentId={} error={}",
                    eventRecordId, paymentId, exception.getMessage());
        } catch (RuntimeException exception) {
            recorder.markFailed(eventRecordId, exception.getMessage());
            log.error("failed to process mercado pago webhook eventRecordId={} paymentId={} error={}",
                    eventRecordId, paymentId, exception.getMessage(), exception);
        }
    }

    private boolean isPaymentEvent(String eventType) {
        return !StringUtils.hasText(eventType) || eventType.startsWith("payment");
    }

    private String extractPaymentId(JsonNode payload) {
        String directId = textAt(payload, "data.id");
        if (StringUtils.hasText(directId)) {
            return directId;
        }
        String resource = textAt(payload, "resource");
        if (StringUtils.hasText(resource) && resource.contains("/")) {
            return resource.substring(resource.lastIndexOf('/') + 1);
        }
        return resource;
    }

    private String textAt(JsonNode payload, String path) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload;
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public record WebhookHeaders(String signature, String requestId, String queryDataId) {
    }
}
