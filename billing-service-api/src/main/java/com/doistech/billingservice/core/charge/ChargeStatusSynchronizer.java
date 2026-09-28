package com.doistech.billingservice.core.charge;

import com.doistech.billingservice.callback.CallbackPublisher;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentRepository;
import com.doistech.billingservice.gateway.mercadopago.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.mercadopago.MercadoPagoStatusMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Aplica o estado de um pagamento do gateway sobre a charge e a invoice, registra o Payment e enfileira o
 * callback. Usado pelo webhook, pela criação síncrona (cartão) e pelo cancelamento.
 */
@Component
public class ChargeStatusSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ChargeStatusSynchronizer.class);

    private static final Set<ChargeStatus> FAILED = EnumSet.of(
            ChargeStatus.REJECTED, ChargeStatus.CANCELED, ChargeStatus.EXPIRED, ChargeStatus.ERROR);

    static final Map<ChargeStatus, String> EVENTS = Map.of(
            ChargeStatus.PAID, "charge.paid",
            ChargeStatus.REJECTED, "charge.rejected",
            ChargeStatus.CANCELED, "charge.canceled",
            ChargeStatus.EXPIRED, "charge.expired",
            ChargeStatus.REFUNDED, "charge.refunded");

    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CallbackPublisher callbackPublisher;

    public ChargeStatusSynchronizer(
            ChargeRepository chargeRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            CallbackPublisher callbackPublisher
    ) {
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.callbackPublisher = callbackPublisher;
    }

    /**
     * @return true quando o status da charge mudou.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Charge charge, GatewayPaymentResponse payment) {
        copyGatewayDetails(charge, payment);
        if (StringUtils.hasText(payment.gatewayPaymentId())) {
            upsertPayment(charge, payment);
        }

        ChargeStatus next = MercadoPagoStatusMapper.toChargeStatus(payment.gatewayStatus(), payment.statusDetail());
        ChargeStatus previous = charge.getStatus();
        if (!previous.canTransitionTo(next)) {
            chargeRepository.save(charge);
            log.info("charge status kept chargeId={} status={} gatewayStatus={}",
                    charge.getId(), previous, payment.gatewayStatus());
            return false;
        }

        charge.setStatus(next);
        if (next == ChargeStatus.PAID) {
            charge.setPaidAt(Optional.ofNullable(payment.paidAt()).orElse(LocalDateTime.now()));
        }
        chargeRepository.save(charge);
        updateInvoice(charge);
        log.info("charge status changed chargeId={} {} -> {} gatewayStatus={} statusDetail={}",
                charge.getId(), previous, next, payment.gatewayStatus(), payment.statusDetail());

        String event = EVENTS.get(next);
        if (event != null) {
            callbackPublisher.publish(event, charge);
        }
        return true;
    }

    /**
     * Marca uma charge como cancelada localmente (Checkout Pro sem pagamento, por exemplo).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelLocally(Charge charge) {
        if (!charge.getStatus().canTransitionTo(ChargeStatus.CANCELED)) {
            return;
        }
        charge.setStatus(ChargeStatus.CANCELED);
        chargeRepository.save(charge);
        updateInvoice(charge);
        callbackPublisher.publish(EVENTS.get(ChargeStatus.CANCELED), charge);
    }

    /**
     * Registra um estorno confirmado pelo gateway. Estorno total leva charge e invoice para REFUNDED.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyRefund(Charge charge, BigDecimal refundedAmount) {
        BigDecimal total = charge.getRefundedAmount().add(refundedAmount);
        charge.setRefundedAmount(total);
        if (total.compareTo(charge.getAmount()) >= 0) {
            charge.setStatus(ChargeStatus.REFUNDED);
        }
        chargeRepository.save(charge);
        updateInvoice(charge);
        callbackPublisher.publish(EVENTS.get(ChargeStatus.REFUNDED), charge);
    }

    private void updateInvoice(Charge charge) {
        Invoice invoice = charge.getInvoice();
        InvoiceStatus current = invoice.getStatus();
        if (charge.getStatus() == ChargeStatus.PAID && current != InvoiceStatus.PAID && current != InvoiceStatus.REFUNDED) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(charge.getPaidAt());
        } else if (charge.getStatus() == ChargeStatus.REFUNDED && current == InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.REFUNDED);
        } else if (FAILED.contains(charge.getStatus()) && current == InvoiceStatus.WAITING_PAYMENT) {
            // A tentativa falhou: a invoice volta a aceitar uma nova charge.
            invoice.setStatus(InvoiceStatus.OPEN);
        } else {
            return;
        }
        invoiceRepository.save(invoice);
    }

    private void copyGatewayDetails(Charge charge, GatewayPaymentResponse payment) {
        if (StringUtils.hasText(payment.gatewayPaymentId())) {
            charge.setGatewayPaymentId(payment.gatewayPaymentId());
        }
        if (payment.statusDetail() != null) {
            charge.setStatusDetail(payment.statusDetail());
        }
        if (payment.installments() != null) {
            charge.setInstallments(payment.installments());
        }
        if (payment.pixQrCode() != null) {
            charge.setPixQrCode(payment.pixQrCode());
            charge.setPixQrCodeBase64(payment.pixQrCodeBase64());
        }
        if (payment.ticketUrl() != null) {
            charge.setTicketUrl(payment.ticketUrl());
        }
        if (payment.boletoBarcode() != null) {
            charge.setBoletoBarcode(payment.boletoBarcode());
        }
        if (payment.boletoDigitableLine() != null) {
            charge.setBoletoDigitableLine(payment.boletoDigitableLine());
        }
        if (payment.expiresAt() != null) {
            charge.setExpiresAt(payment.expiresAt());
        }
        if (payment.refundedAmount() != null && payment.refundedAmount().compareTo(charge.getRefundedAmount()) > 0) {
            charge.setRefundedAmount(payment.refundedAmount());
        }
    }

    private void upsertPayment(Charge charge, GatewayPaymentResponse response) {
        Payment payment = paymentRepository.findByGatewayAndGatewayPaymentId(response.gateway(), response.gatewayPaymentId())
                .orElseGet(() -> {
                    Payment created = new Payment();
                    created.setMerchantId(charge.getMerchantId());
                    created.setInvoice(charge.getInvoice());
                    created.setCharge(charge);
                    created.setGateway(response.gateway());
                    created.setGatewayPaymentId(response.gatewayPaymentId());
                    return created;
                });
        payment.setGatewayStatus(response.gatewayStatus());
        payment.setAmount(response.amount() == null ? charge.getAmount() : response.amount());
        payment.setPaidAt(response.paidAt());
        payment.setRawResponse(response.rawResponse());
        paymentRepository.save(payment);
    }
}
