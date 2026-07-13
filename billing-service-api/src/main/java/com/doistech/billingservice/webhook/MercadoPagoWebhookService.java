package com.doistech.billingservice.webhook;

import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeRepository;
import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentRepository;
import com.doistech.billingservice.gateway.mercadopago.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.mercadopago.PaymentGatewayService;
import com.doistech.billingservice.shared.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MercadoPagoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final InvoiceRepository invoiceRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;

    public MercadoPagoWebhookService(
            WebhookEventRepository webhookEventRepository,
            PaymentGatewayService paymentGatewayService,
            InvoiceRepository invoiceRepository,
            ChargeRepository chargeRepository,
            PaymentRepository paymentRepository
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.invoiceRepository = invoiceRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void receive(JsonNode payload) {
        WebhookEvent event = new WebhookEvent();
        event.setGateway(Gateway.MERCADO_PAGO);
        event.setEventId(textAt(payload, "id"));
        event.setEventType(firstNonBlank(textAt(payload, "type"), textAt(payload, "action")));
        event.setGatewayPaymentId(extractPaymentId(payload));
        event.setPayload(payload);
        event.setProcessed(false);
        webhookEventRepository.save(event);
        log.info(
                "mercado pago webhook received eventRecordId={} eventId={} eventType={} paymentId={}",
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getGatewayPaymentId());

        try {
            if (!StringUtils.hasText(event.getGatewayPaymentId())) {
                throw new BusinessRuleException("gateway payment id not found in webhook payload");
            }

            GatewayPaymentResponse paymentResponse = paymentGatewayService.getPayment(event.getGatewayPaymentId());
            Invoice invoice = resolveInvoice(paymentResponse);
            log.info(
                    "webhook resolved invoice eventRecordId={} paymentId={} invoiceId={} invoiceStatus={} gatewayStatus={}",
                    event.getId(),
                    paymentResponse.gatewayPaymentId(),
                    invoice.getId(),
                    invoice.getStatus(),
                    paymentResponse.gatewayStatus());

            if (invoice.getStatus() == InvoiceStatus.PAID) {
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                webhookEventRepository.save(event);
                log.info(
                        "webhook ignored because invoice already paid eventRecordId={} paymentId={} invoiceId={}",
                        event.getId(),
                        paymentResponse.gatewayPaymentId(),
                        invoice.getId());
                return;
            }

            Charge charge = chargeRepository.findFirstByInvoiceIdOrderByCreatedAtDesc(invoice.getId())
                    .orElseThrow(() -> new BusinessRuleException("charge not found for invoice"));

            if (paymentRepository.existsByGatewayAndGatewayPaymentId(Gateway.MERCADO_PAGO, paymentResponse.gatewayPaymentId())) {
                log.info(
                        "payment already exists for webhook eventRecordId={} paymentId={} invoiceId={}",
                        event.getId(),
                        paymentResponse.gatewayPaymentId(),
                        invoice.getId());
            } else {
                savePaymentIdempotently(event, paymentResponse, invoice, charge);
            }

            charge.setGatewayPaymentId(paymentResponse.gatewayPaymentId());
            charge.setStatus(mapChargeStatus(paymentResponse.gatewayStatus()));
            chargeRepository.save(charge);
            log.info(
                    "charge updated from webhook eventRecordId={} chargeId={} invoiceId={} chargeStatus={} paymentId={}",
                    event.getId(),
                    charge.getId(),
                    invoice.getId(),
                    charge.getStatus(),
                    paymentResponse.gatewayPaymentId());

            if (charge.getStatus() == ChargeStatus.PAID && invoice.getStatus() != InvoiceStatus.PAID) {
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setPaidAt(Optional.ofNullable(paymentResponse.paidAt()).orElse(LocalDateTime.now()));
                invoiceRepository.save(invoice);
                log.info(
                        "invoice marked paid from webhook eventRecordId={} invoiceId={} paidAt={}",
                        event.getId(),
                        invoice.getId(),
                        invoice.getPaidAt());
            }

            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);
            log.info(
                    "mercado pago webhook processed successfully eventRecordId={} eventId={} paymentId={} invoiceId={}",
                    event.getId(),
                    event.getEventId(),
                    paymentResponse.gatewayPaymentId(),
                    invoice.getId());
        } catch (RuntimeException exception) {
            log.error(
                    "failed to process mercado pago webhook eventRecordId={} eventId={} eventType={} paymentId={} error={}",
                    event.getId(),
                    event.getEventId(),
                    event.getEventType(),
                    event.getGatewayPaymentId(),
                    exception.getMessage(),
                    exception);
            event.setErrorMessage(exception.getMessage());
            webhookEventRepository.save(event);
        }
    }

    private Invoice resolveInvoice(GatewayPaymentResponse paymentResponse) {
        String externalReference = paymentResponse.externalReference();
        if (!StringUtils.hasText(externalReference)) {
            throw new BusinessRuleException("payment external reference not found");
        }

        UUID invoiceId = parseInvoiceId(externalReference);
        if (invoiceId == null) {
            throw new BusinessRuleException("invoice id not found in external reference");
        }

        return invoiceRepository.findDetailedById(invoiceId)
                .orElseThrow(() -> new BusinessRuleException("invoice not found"));
    }

    private void savePaymentIdempotently(
            WebhookEvent event,
            GatewayPaymentResponse paymentResponse,
            Invoice invoice,
            Charge charge
    ) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setCharge(charge);
        payment.setGateway(Gateway.MERCADO_PAGO);
        payment.setGatewayPaymentId(paymentResponse.gatewayPaymentId());
        payment.setGatewayStatus(paymentResponse.gatewayStatus());
        payment.setAmount(paymentResponse.amount());
        payment.setPaidAt(paymentResponse.paidAt());
        payment.setRawResponse(paymentResponse.rawResponse());

        try {
            paymentRepository.save(payment);
            log.info(
                    "payment created from webhook eventRecordId={} paymentId={} invoiceId={} chargeId={} amount={} gatewayStatus={}",
                    event.getId(),
                    paymentResponse.gatewayPaymentId(),
                    invoice.getId(),
                    charge.getId(),
                    paymentResponse.amount(),
                    paymentResponse.gatewayStatus());
        } catch (DataIntegrityViolationException exception) {
            log.info(
                    "payment creation raced with another webhook eventRecordId={} paymentId={} invoiceId={} message={}",
                    event.getId(),
                    paymentResponse.gatewayPaymentId(),
                    invoice.getId(),
                    exception.getMostSpecificCause() != null
                            ? exception.getMostSpecificCause().getMessage()
                            : exception.getMessage());
        }
    }

    private UUID parseInvoiceId(String externalReference) {
        for (String segment : externalReference.split("\\|")) {
            if (segment.startsWith("INVOICE:")) {
                return UUID.fromString(segment.substring("INVOICE:".length()));
            }
        }
        return null;
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

    private ChargeStatus mapChargeStatus(String gatewayStatus) {
        if ("approved".equalsIgnoreCase(gatewayStatus)) {
            return ChargeStatus.PAID;
        }
        if ("pending".equalsIgnoreCase(gatewayStatus) || "in_process".equalsIgnoreCase(gatewayStatus)) {
            return ChargeStatus.WAITING_PAYMENT;
        }
        if ("rejected".equalsIgnoreCase(gatewayStatus)) {
            return ChargeStatus.REJECTED;
        }
        if ("cancelled".equalsIgnoreCase(gatewayStatus) || "refunded".equalsIgnoreCase(gatewayStatus)) {
            return ChargeStatus.CANCELED;
        }
        return ChargeStatus.ERROR;
    }

    private String textAt(JsonNode payload, String path) {
        JsonNode node = payload;
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
