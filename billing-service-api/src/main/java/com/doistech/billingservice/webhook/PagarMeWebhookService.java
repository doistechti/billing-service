package com.doistech.billingservice.webhook;

import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.doistech.billingservice.shared.InvalidSignatureException;
import com.doistech.billingservice.shared.NotFoundException;
import com.doistech.billingservice.shared.crypto.Hmac;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Webhook do Pagar.me (SDD 10). Eventos {@code charge.*} trazem a charge em {@code data}; eventos {@code order.*}
 * trazem o order, e a charge vem em {@code data.charges[0]}. Qualquer outro evento é registrado e ignorado.
 *
 * <p>O payload nunca confirma pagamento: o {@link WebhookPaymentProcessor} consulta a charge no Pagar.me com a
 * secret key do merchant. A autenticação do envio (segredo do merchant) é uma camada adicional; o esquema de
 * assinatura oficial do Pagar.me ainda está nas pendências do SDD 10.
 */
@Service
public class PagarMeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PagarMeWebhookService.class);

    private final MerchantService merchantService;
    private final WebhookEventRecorder recorder;
    private final WebhookPaymentProcessor processor;

    public PagarMeWebhookService(
            MerchantService merchantService,
            WebhookEventRecorder recorder,
            WebhookPaymentProcessor processor
    ) {
        this.merchantService = merchantService;
        this.recorder = recorder;
        this.processor = processor;
    }

    /**
     * Lança {@link GatewayIntegrationException} transitória quando o Pagar.me deve reenviar; nos demais casos
     * registra o erro e responde normalmente.
     */
    public void receive(UUID merchantId, JsonNode payload, String authorization, String token) {
        if (merchantService.getById(merchantId).getGateway() != Gateway.PAGAR_ME) {
            throw new NotFoundException("merchant not found");
        }

        Optional<String> secret = merchantService.webhookSecret(merchantId);
        if (secret.isPresent() && !isAuthorized(secret.get(), authorization, token)) {
            log.warn("pagar.me webhook rejected: invalid credentials merchantId={}", merchantId);
            throw new InvalidSignatureException("invalid webhook credentials");
        }
        if (secret.isEmpty()) {
            log.warn("pagar.me webhook accepted without secret merchantId={}: payment is still confirmed by lookup",
                    merchantId);
        }

        String eventType = text(payload, "type");
        String chargeId = chargeId(eventType, payload);
        UUID eventRecordId = recorder.record(Gateway.PAGAR_ME, merchantId, text(payload, "id"), eventType, chargeId, payload);
        log.info("pagar.me webhook received merchantId={} eventRecordId={} eventType={} chargeId={}",
                merchantId, eventRecordId, eventType, chargeId);

        if (!StringUtils.hasText(chargeId)) {
            recorder.markProcessed(eventRecordId);
            log.info("pagar.me webhook ignored eventRecordId={} eventType={}", eventRecordId, eventType);
            return;
        }

        try {
            processor.process(merchantId, chargeId);
            recorder.markProcessed(eventRecordId);
        } catch (GatewayIntegrationException exception) {
            recorder.markFailed(eventRecordId, exception.getMessage());
            if (exception.isTransient()) {
                log.error("pagar.me webhook will be retried eventRecordId={} chargeId={} error={}",
                        eventRecordId, chargeId, exception.getMessage());
                throw exception;
            }
            log.warn("pagar.me webhook not processed eventRecordId={} chargeId={} error={}",
                    eventRecordId, chargeId, exception.getMessage());
        } catch (RuntimeException exception) {
            recorder.markFailed(eventRecordId, exception.getMessage());
            log.error("failed to process pagar.me webhook eventRecordId={} chargeId={} error={}",
                    eventRecordId, chargeId, exception.getMessage(), exception);
        }
    }

    /** Segredo do merchant na senha do Basic auth do endpoint ou no parâmetro {@code token}. */
    private boolean isAuthorized(String secret, String authorization, String token) {
        boolean byToken = Hmac.constantTimeEquals(secret, token);
        boolean byBasic = Hmac.constantTimeEquals(secret, basicPassword(authorization));
        return byToken || byBasic;
    }

    private String basicPassword(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            return separator < 0 ? null : decoded.substring(separator + 1);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String chargeId(String eventType, JsonNode payload) {
        if (eventType == null || payload == null) {
            return null;
        }
        JsonNode data = payload.path("data");
        if (eventType.startsWith("charge.")) {
            return text(data, "id");
        }
        if (eventType.startsWith("order.")) {
            return text(data.path("charges").path(0), "id");
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
