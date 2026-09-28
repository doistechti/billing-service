package com.doistech.billingservice.callback;

import com.doistech.billingservice.callback.CallbackQueue.ClaimedCallback;
import com.doistech.billingservice.config.CallbackProperties;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.merchant.MerchantService.CallbackTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia os callbacks pendentes para o sistema cliente, com assinatura HMAC e novas tentativas.
 */
@Component
public class CallbackDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CallbackDispatcher.class);

    private final CallbackQueue queue;
    private final MerchantService merchantService;
    private final CallbackProperties properties;
    private final RestClient restClient;

    public CallbackDispatcher(
            CallbackQueue queue,
            MerchantService merchantService,
            CallbackProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.queue = queue;
        this.merchantService = merchantService;
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    @Scheduled(fixedDelayString = "${app.callbacks.poll-interval:PT10S}")
    public void scheduledDispatch() {
        if (properties.enabled()) {
            dispatchDue();
        }
    }

    public int dispatchDue() {
        List<ClaimedCallback> batch = queue.claimDue();
        for (ClaimedCallback callback : batch) {
            deliver(callback);
        }
        return batch.size();
    }

    private void deliver(ClaimedCallback callback) {
        Optional<CallbackTarget> target = merchantService.callbackTarget(callback.merchantId());
        if (target.isEmpty()) {
            queue.markFailed(callback.id(), null, "callback url or secret not configured");
            return;
        }
        long timestamp = Instant.now().getEpochSecond();
        try {
            int status = restClient.post()
                    .uri(target.get().url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Billing-Event", callback.eventType())
                    .header("X-Billing-Delivery", callback.id().toString())
                    .header("X-Billing-Timestamp", String.valueOf(timestamp))
                    .header("X-Billing-Signature", CallbackSigner.sign(target.get().secret(), timestamp, callback.payload()))
                    .body(callback.payload())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .value();
            queue.markDelivered(callback.id(), status);
            log.info("callback delivered deliveryId={} event={} status={}", callback.id(), callback.eventType(), status);
        } catch (RestClientResponseException exception) {
            queue.markFailed(callback.id(), exception.getStatusCode().value(), exception.getStatusText());
            log.warn("callback rejected deliveryId={} event={} status={}",
                    callback.id(), callback.eventType(), exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            queue.markFailed(callback.id(), null, exception.getMessage());
            log.warn("callback failed deliveryId={} event={} error={}",
                    callback.id(), callback.eventType(), exception.getMessage());
        }
    }
}
